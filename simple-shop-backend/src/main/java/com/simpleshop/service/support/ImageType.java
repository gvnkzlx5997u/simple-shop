package com.simpleshop.service.support;

import java.util.Optional;

/**
 * 允许上传的图片类型，及其<b>按内容</b>的识别（{@code C-14}、{@code NFR-11}、方案 §6）。
 *
 * <h2>⚠️ 为什么必须读魔数，而不是看扩展名或 {@code Content-Type}</h2>
 * <p>方案 §6 明确要求「<b>必须校验真实内容，不能只看扩展名或 {@code Content-Type}</b>」。
 * 原因是这两者都<b>完全由客户端提供</b>：
 * <ul>
 *   <li>{@code Content-Type} 是请求头的一个字段，攻击者（或只是配置错误的客户端）可以随意填
 *       {@code image/png} 而实际发送任意字节；</li>
 *   <li>文件名后缀同理。若只按后缀命名并落盘，一个 {@code .jpg} 文件里可以是 HTML ——
 *       将来若该目录被以其它方式暴露或下载，就是存储型 XSS 的载体（{@code NFR-20} 的同类风险）。</li>
 * </ul>
 * <p>因此本枚举只看文件<b>开头的魔数</b>，并把<b>服务端识别出的</b>类型作为扩展名唯一来源
 * （方案 §6「文件名由服务端生成，不采用用户原始文件名」）。
 *
 * <h2>⚠️ 本类刻意<b>不</b>做「图片能否解码」的完整校验</h2>
 * <p>{@code ImageIO.read()} 能进一步发现「魔数对但内容截断」的文件，但它会把整张图解码进内存
 * （大图可致内存放大），且对纯校验而言并非必需。契约（{@code C-14}）要求的是
 * 「JPG／PNG + 单张 ≤5MB」——魔数恰好回答「是不是 JPG／PNG」，大小由调用方回答。
 * 若将来要求「损坏图片也拒绝」，应改用带尺寸上限的流式解码，而不是直接用 {@code ImageIO.read}。
 */
public enum ImageType {

    /**
     * JPEG。
     *
     * <p>魔数 {@code FF D8 FF}：{@code FF D8} 是 SOI（图像起始）标记，第三个字节 {@code FF}
     * 是紧随其后的标记段前缀。只判 {@code FF D8} 会放过「只有两个字节的假文件」，
     * 故判三个字节。
     */
    JPEG("jpg", 0xFF, 0xD8, 0xFF),

    /**
     * PNG。
     *
     * <p>魔数 8 字节 {@code 89 50 4E 47 0D 0A 1A 0A}：其中的 {@code 0D 0A} 与 {@code 1A}
     * 是刻意选的——它们能让「文本模式传输」与「DOS 的 EOF 截断」把文件弄坏时立刻暴露。
     * 8 字节全比，不做前缀放宽。
     */
    PNG("png", 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);

    private final String extension;
    private final byte[] magic;

    ImageType(String extension, int... magic) {
        this.extension = extension;
        this.magic = new byte[magic.length];
        for (int i = 0; i < magic.length; i++) {
            this.magic[i] = (byte) magic[i];
        }
    }

    /** 规范化扩展名（小写、无点）。既是落盘后缀，也是 {@code pic_url} 的一部分。 */
    public String extension() {
        return extension;
    }

    /**
     * 按内容识别图片类型。
     *
     * @param content 文件内容（可长于魔数；只需保证开头若干字节可用）
     * @return 识别出的类型；内容为 {@code null}、过短或不匹配任何魔数时返回 {@link Optional#empty()}
     */
    public static Optional<ImageType> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        for (ImageType type : values()) {
            if (type.matches(content)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    private boolean matches(byte[] content) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
