package com.carytm.music.auth

import fi.iki.elonen.NanoHTTPD

class CookieImportServer(
    port: Int = 8888,
    private val onCookieReceived: (String) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.POST) {
            val files = HashMap<String, String>()
            try {
                session.parseBody(files)
                val postData = session.parameters["cookies"]?.firstOrNull() ?: ""
                if (postData.isNotBlank()) {
                    onCookieReceived(postData.trim())
                    val successHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                        <title>导入成功</title>
                        <style>body{background:#121212;color:#fff;font-family:sans-serif;text-align:center;padding:40px;}</style>
                        </head>
                        <body>
                            <h2 style="color:#4CAF50;">✓ Cookie 导入成功！</h2>
                            <p>车机已同步您的 Cookie，正在刷新个人歌单。您可以关闭本页面了。</p>
                        </body>
                        </html>
                    """.trimIndent()
                    return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", successHtml)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Return submission form
        val formHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>CarYTM - 导入 YouTube Music Cookie</title>
                <style>
                    body { background: #121212; color: #fff; font-family: -apple-system, sans-serif; padding: 20px; max-width: 500px; margin: 0 auto; }
                    h2 { color: #ff0000; }
                    textarea { width: 100%; height: 160px; background: #222; color: #fff; border: 1px solid #444; border-radius: 8px; padding: 10px; font-size: 14px; box-sizing: border-box; }
                    button { width: 100%; height: 50px; background: #ff0000; color: #fff; border: none; border-radius: 8px; font-size: 16px; font-weight: bold; margin-top: 15px; cursor: pointer; }
                    .note { color: #888; font-size: 13px; line-height: 1.5; margin-top: 15px; }
                </style>
            </head>
            <body>
                <h2>CarYTM Cookie 导入助手</h2>
                <p>将您在电脑或手机浏览器登录 YouTube Music 后获取的 Cookie 粘贴至下方：</p>
                <form method="POST">
                    <textarea name="cookies" placeholder="粘贴 Cookie（如 SAPISID=...; SSID=...）" required></textarea>
                    <button type="submit">一键发送到车机</button>
                </form>
                <div class="note">
                    <strong>提示：</strong>此服务仅在车机本地局域网（同一 Wi-Fi 或车机热点）运行，绝不上传到任何第三方服务器，保障账号绝对隐私安全。
                </div>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", formHtml)
    }
}
