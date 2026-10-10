package com.simpleshop.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;

import com.simpleshop.config.AppProperties;
import com.simpleshop.session.SessionStore;
import com.simpleshop.testing.TestImageDirectory;

/**
 * 图片上传在<b>真实 servlet 容器</b>里的行为验证（{@code I11-05}、{@code NFR-11}）。
 *
 * <h2>⚠️ 为什么必须有这个类：MockMvc <b>测不到</b>这里要测的东西</h2>
 * <p>{@code SellerGoodsApiTest} 用 MockMvc，它直接构造
 * {@code MockMultipartHttpServletRequest}（文件已经是内存里的 {@code MultipartFile}），
 * <b>既不经过 Tomcat 的 multipart 解析器，也没有真实连接</b>。因此它测不到：
 * <ul>
 *   <li>{@code spring.servlet.multipart.max-file-size} 是否真的生效；</li>
 *   <li>更关键的——<b>超限时错误响应到底能不能送达客户端</b>。</li>
 * </ul>
 *
 * <h2>本类存在的直接原因（真实端口实测发现的缺陷）</h2>
 * <p>只配 {@code max-file-size: 5MB} 时，上传 6MB 的真实结果是：客户端收到
 * <pre>
 * Unable to write data to the transport connection: 远程主机强迫关闭了一个现有的连接
 * </pre>
 * 也就是<b>连接被直接断开，契约要求的 {@code 20009} 与 {@code M10-31} 根本没送达</b>。
 * 原因是 Tomcat 抛异常那一刻请求体还剩一大截没读，而它默认只肯「吞掉」2MB
 * （{@code max-swallow-size}），超出就直接断连。
 * <p>该缺陷恰好命中 {@code NFR-11} 的可测指标「绕过前端直传 >5MB 仍被拒绝<b>并提示 M10-31</b>」
 * 与验收方式 {@code V-02}「必须包含绕过前端的负向调用」——而修复它只需要一行配置
 * （{@code server.tomcat.max-swallow-size: 10MB}），
 * <b>因而没有自动化用例守护的话，它极易被当成「无用配置」删掉</b>。
 * 本类就是那条守护线。
 *
 * <h2>为什么这里不测「超过 max-swallow-size 的 30MB」</h2>
 * <p>那种请求的连接仍会被断开（这是有意的取值取舍，见 {@code application.yml} 的说明）。
 * 断言「连接被断开」需要依赖客户端异常的具体形态，脆弱且没有业务价值——
 * 一个 30MB 的请求打到只允许 5MB 的接口上，被拒绝这件事本身没有歧义。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SellerImageUploadContainerTest {

    private static final String IMAGE_PATH = SellerProductController.BASE_PATH + "/image";

    /** 合法的最小 JPEG（魔数 + 少量填充）——内容检查只看魔数，故足够。 */
    private static final byte[] TINY_JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private AppProperties properties;

    private String token;

    @AfterEach
    void cleanUp() {
        sessionStore.removeAllFor("seller");
        TestImageDirectory.delete(properties);
    }

    @Test
    @DisplayName("真实容器：超过 5MB 的上传必须拿到 HTTP 200 + code 20009（而不是连接被断开）")
    void oversizedUploadReceivesContractResponseFromRealContainer() {
        // 6MB：略超 5MB 的 max-file-size，正是「绕过前端直传」的现实形态
        byte[] oversized = jpegPaddedTo(6 * 1024 * 1024);

        ResponseEntity<String> response;
        try {
            response = upload(oversized, "big.jpg");
        } catch (ResourceAccessException e) {
            // 这正是「配置被删掉」时的失败形态：连接被服务端断开，客户端拿不到任何响应。
            // 直接把这个因果写进失败信息，免得排查的人从一条 socket 异常开始猜。
            throw new AssertionError("超限上传时连接被断开，契约要求的 20009 没有送达。"
                    + "请检查 application.yml 里的 server.tomcat.max-swallow-size："
                    + "它必须不小于要「吞掉」的请求体大小，否则 Tomcat 会直接断连。原因: "
                    + e.getMessage(), e);
        }

        assertThat(response.getStatusCode().value())
                .as("必须是一个正常的 HTTP 响应，而不是连接异常")
                .isEqualTo(200);
        assertThat(response.getBody())
                .contains("\"code\":20009")
                // message 必须是 ASCII 英文（已定 Q-13），且明确指出限制
                .contains("5MB");
    }

    @Test
    @DisplayName("真实容器：合法小图仍能上传成功（确认上面的限制没有误伤正常图片）")
    void smallValidUploadStillSucceeds() {
        ResponseEntity<String> response = upload(TINY_JPEG, "tiny.jpg");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .contains("\"code\":0")
                .contains("/images/")
                .contains(".jpg");
    }

    @Test
    @DisplayName("真实容器：非图片内容（文本）→ 20009，与 MockMvc 用例结论一致")
    void nonImageUploadIsRejectedByRealContainerToo() {
        ResponseEntity<String> response = upload(
                "definitely not an image".getBytes(StandardCharsets.UTF_8), "notes.txt");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"code\":20009");
    }

    // =========================================================================
    // 辅助
    // =========================================================================

    /** 以 multipart 形式 POST 一个文件到 {@code I11-05}。 */
    private ResponseEntity<String> upload(byte[] content, String filename) {
        if (token == null) {
            // 直接建会话，不绕 HTTP 登录：本类关心的是上传链路，
            // 且这样能避免与 SellerAuthApiTest 改密用例产生「口令是不是种子值」的隐式依赖。
            token = sessionStore.create("seller").token();
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                // 必须覆写：否则 Spring 的 FormHttpMessageConverter 无法确定文件名而报错
                return filename;
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);

        return restTemplate.postForEntity(IMAGE_PATH, new HttpEntity<>(body, headers), String.class);
    }

    /** 造一个「合法 JPEG 魔数 + 填充」的载荷，长度精确为 {@code length}。 */
    private static byte[] jpegPaddedTo(int length) {
        byte[] content = new byte[length];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        return content;
    }
}
