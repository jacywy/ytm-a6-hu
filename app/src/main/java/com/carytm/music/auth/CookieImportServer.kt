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
                        <title>Import Successful / 导入成功</title>
                        <style>body{background:#121212;color:#fff;font-family:-apple-system,sans-serif;text-align:center;padding:40px;line-height:1.6;}</style>
                        </head>
                        <body>
                            <h2 style="color:#4CAF50;">✓ Cookie Imported Successfully / 导入成功！</h2>
                            <p>Car unit synchronized cookies and is updating playlists. You may close this page now.<br><span style="color:#888;">车机已同步您的 Cookie，正在刷新个人歌单。您可以关闭本页面了。</span></p>
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
                <title>CarYTM - Import YouTube Music Cookie</title>
                <style>
                    body { background: #121212; color: #fff; font-family: -apple-system, sans-serif; padding: 20px; max-width: 500px; margin: 0 auto; line-height: 1.5; }
                    h2 { color: #ff0000; margin-bottom: 6px; }
                    .sub { color: #aaa; font-size: 13px; margin-top: 0; }
                    textarea { width: 100%; height: 160px; background: #222; color: #fff; border: 1px solid #444; border-radius: 8px; padding: 10px; font-size: 14px; box-sizing: border-box; }
                    button { width: 100%; height: 50px; background: #ff0000; color: #fff; border: none; border-radius: 8px; font-size: 16px; font-weight: bold; margin-top: 15px; cursor: pointer; }
                    .note { color: #888; font-size: 12px; line-height: 1.5; margin-top: 15px; border-top: 1px solid #282828; padding-top: 12px; }
                </style>
            </head>
            <body>
                <h2>CarYTM Cookie Import Assistant</h2>
                <p class="sub">Cookie 导入助手</p>
                <p>Paste the YouTube Music Cookie from your browser below:<br><span style="color:#aaa; font-size:13px;">将您在浏览器登录 YouTube Music 后获取的 Cookie 粘贴至下方：</span></p>
                <form method="POST">
                    <textarea name="cookies" placeholder="Paste Cookie here (e.g. SAPISID=...; SSID=...)" required></textarea>
                    <button type="submit">Send to Car / 发送到车机</button>
                </form>
                <div class="note">
                    <strong>Security Notice / 安全提示:</strong> This service runs strictly within your local car network (same Wi-Fi or hotspot). No data is transmitted to external servers.<br>此服务仅在车机本地局域网运行，绝不上传到任何第三方服务器。
                </div>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", formHtml)
    }
}
