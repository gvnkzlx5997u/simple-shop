/**
 * 测试用文件生成。
 *
 * 只造「能被后端认下的最小文件」：1×1 PNG 用来走正常上传，超限图用来打大小校验。
 * 内容不参与断言，所以只要魔数与体积对得上即可，不需要真实可解码的图片。
 */

/** 1×1 PNG（67 字节）：真实文件头 + 最小 IDAT，后端按魔数就能认成 PNG */
export function tinyPng(): Buffer {
  return Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
    'base64',
  );
}

/** 指定字节数的伪 PNG：只用于测大小上限，内容不是有效图片 */
export function fakePng(bytes: number): Buffer {
  const buffer = Buffer.alloc(bytes, 0);
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]).copy(buffer, 0);
  return buffer;
}

/** 上传用的文件描述，直接喂给 helpers/api 的 uploadAftersaleImage */
export function pngFile(name = 'aftersale-1.png') {
  return { name, mimeType: 'image/png', buffer: tinyPng() };
}
