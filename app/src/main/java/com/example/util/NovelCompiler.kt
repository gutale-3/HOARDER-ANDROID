package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.local.BookEntity
import com.example.data.local.ChapterEntity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object NovelCompiler {

    /**
     * Compiles the novel into a standardized, fully valid EPUB 3 file with cover and nav document.
     */
    fun compileEpub(
        context: Context,
        book: BookEntity,
        chapters: List<ChapterEntity>,
        outputFile: File
    ): Boolean {
        if (chapters.isEmpty()) return false
        var zipStream: ZipOutputStream? = null
        try {
            zipStream = ZipOutputStream(FileOutputStream(outputFile))

            // 1. mimetype (MUST be first, uncompressed)
            val mimeEntry = ZipEntry("mimetype")
            mimeEntry.method = ZipEntry.STORED
            val mimeBytes = "application/epub+zip".toByteArray(Charsets.UTF_8)
            mimeEntry.size = mimeBytes.size.toLong()
            mimeEntry.compressedSize = mimeBytes.size.toLong()
            mimeEntry.crc = calculateCrc32(mimeBytes)
            zipStream.putNextEntry(mimeEntry)
            zipStream.write(mimeBytes)
            zipStream.closeEntry()

            // 2. META-INF/container.xml
            zipStream.putNextEntry(ZipEntry("META-INF/container.xml"))
            val containerXml = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent()
            zipStream.write(containerXml.toByteArray(Charsets.UTF_8))
            zipStream.closeEntry()

            // 3. OEBPS/style.css
            zipStream.putNextEntry(ZipEntry("OEBPS/style.css"))
            val css = """
                body {
                    font-family: "Georgia", serif;
                    line-height: 1.6;
                    padding: 5%;
                    color: #111111;
                }
                h1 {
                    text-align: center;
                    margin-bottom: 2em;
                    font-size: 2em;
                }
                h2 {
                    text-align: center;
                    margin-top: 1.5em;
                    margin-bottom: 1em;
                    font-size: 1.5em;
                    border-bottom: 1px solid #eeeeee;
                    padding-bottom: 0.3em;
                }
                p {
                    text-indent: 1.5em;
                    margin: 0 0 0.8em 0;
                    text-align: justify;
                }
                .synopsis {
                    font-style: italic;
                    color: #555555;
                    border-left: 3px solid #cccccc;
                    padding-left: 1em;
                    margin: 2em 0;
                }
                img.cover {
                    max-width: 100%;
                    max-height: 100%;
                    height: auto;
                    width: auto;
                    display: block;
                    margin: 0 auto;
                }
            """.trimIndent()
            zipStream.write(css.toByteArray(Charsets.UTF_8))
            zipStream.closeEntry()

            // Check if cover image exists
            val coverFile = File(outputFile.parentFile, "cover.jpg")
            val hasCover = coverFile.exists() && coverFile.length() > 0

            // 4. If cover exists, write cover.jpg and cover.xhtml
            if (hasCover) {
                // Write OEBPS/cover.jpg
                zipStream.putNextEntry(ZipEntry("OEBPS/cover.jpg"))
                coverFile.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var len: Int
                    while (input.read(buffer).also { len = it } != -1) {
                        zipStream.write(buffer, 0, len)
                    }
                }
                zipStream.closeEntry()

                // Write OEBPS/cover.xhtml
                zipStream.putNextEntry(ZipEntry("OEBPS/cover.xhtml"))
                val coverXhtml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE html>
                    <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="en" lang="en">
                    <head>
                        <title>Cover - ${escapeXml(book.title)}</title>
                        <link rel="stylesheet" type="text/css" href="style.css" />
                    </head>
                    <body style="margin:0; padding:0; background-color:#ffffff;">
                        <div style="text-align:center; height:100vh; display:flex; align-items:center; justify-content:center;">
                            <img class="cover" src="cover.jpg" alt="Cover Image" />
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                zipStream.write(coverXhtml.toByteArray(Charsets.UTF_8))
                zipStream.closeEntry()
            }

            // 5. OEBPS/about.xhtml (Introduction)
            zipStream.putNextEntry(ZipEntry("OEBPS/about.xhtml"))
            val synopsisParas = book.synopsis
                .split("\n")
                .map { it.replace("\r", "").trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n") { "<p>${escapeXml(it)}</p>" }

            val aboutHtml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="en" lang="en">
                <head>
                    <title>About - ${escapeXml(book.title)}</title>
                    <link rel="stylesheet" type="text/css" href="style.css" />
                </head>
                <body>
                    <h1>${escapeXml(book.title)}</h1>
                    <p style="text-align: center; font-weight: bold;">Author: ${escapeXml(book.author)}</p>
                    <div class="synopsis">
                        $synopsisParas
                    </div>
                </body>
                </html>
            """.trimIndent()
            zipStream.write(aboutHtml.toByteArray(Charsets.UTF_8))
            zipStream.closeEntry()

            // 6. OEBPS/nav.xhtml (EPUB 3 Navigation Document)
            zipStream.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
            val navListItems = chapters.map { ch ->
                """<li><a href="chapter_${ch.chapterNumber}.xhtml">${escapeXml(ch.title)}</a></li>"""
            }.joinToString("\n                ")
            val navHtml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="en" lang="en">
                <head>
                    <title>Table of Contents</title>
                    <link rel="stylesheet" type="text/css" href="style.css" />
                </head>
                <body>
                    <nav epub:type="toc" id="toc">
                        <h1>Table of Contents</h1>
                        <ol>
                            <li><a href="about.xhtml">About</a></li>
                            $navListItems
                        </ol>
                    </nav>
                </body>
                </html>
            """.trimIndent()
            zipStream.write(navHtml.toByteArray(Charsets.UTF_8))
            zipStream.closeEntry()

            // 7. OEBPS/chapter_X.xhtml files
            for (chapter in chapters) {
                zipStream.putNextEntry(ZipEntry("OEBPS/chapter_${chapter.chapterNumber}.xhtml"))
                val contentParas = chapter.content
                    .split("\n")
                    .map { it.replace("\r", "").trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n") { "<p>${escapeXml(it)}</p>" }

                val chapHtml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE html>
                    <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="en" lang="en">
                    <head>
                        <title>${escapeXml(chapter.title)}</title>
                        <link rel="stylesheet" type="text/css" href="style.css" />
                    </head>
                    <body>
                        <section epub:type="chapter">
                            <h2>${escapeXml(chapter.title)}</h2>
                            $contentParas
                        </section>
                    </body>
                    </html>
                """.trimIndent()
                zipStream.write(chapHtml.toByteArray(Charsets.UTF_8))
                zipStream.closeEntry()
            }

            // 8. OEBPS/toc.ncx (Table of Contents for EPUB2 backwards compatibility)
            zipStream.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            val navPoints = chapters.mapIndexed { idx, ch ->
                """
                <navPoint id="navpoint-${idx + 1}" playOrder="${idx + 1}">
                  <navLabel><text>${escapeXml(ch.title)}</text></navLabel>
                  <content src="chapter_${ch.chapterNumber}.xhtml"/>
                </navPoint>
                """.trimIndent()
            }.joinToString("\n")

            val ncxXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                  <head>
                    <meta name="dtb:uid" content="${book.id}"/>
                    <meta name="dtb:depth" content="1"/>
                    <meta name="dtb:totalPageCount" content="0"/>
                    <meta name="dtb:maxPageNumber" content="0"/>
                  </head>
                  <docTitle><text>${escapeXml(book.title)}</text></docTitle>
                  <navMap>
                    <navPoint id="navpoint-about" playOrder="0">
                      <navLabel><text>About</text></navLabel>
                      <content src="about.xhtml"/>
                    </navPoint>
                    $navPoints
                  </navMap>
                </ncx>
            """.trimIndent()
            zipStream.write(ncxXml.toByteArray(Charsets.UTF_8))
            zipStream.closeEntry()

            // 9. OEBPS/content.opf (Book manifest & packaging metadata)
            zipStream.putNextEntry(ZipEntry("OEBPS/content.opf"))
            
            val manifestItems = chapters.map { ch ->
                """<item id="chap_${ch.chapterNumber}" href="chapter_${ch.chapterNumber}.xhtml" media-type="application/xhtml+xml"/>"""
            }.joinToString("\n                    ")

            val spineRefs = chapters.map { ch ->
                """<itemref idref="chap_${ch.chapterNumber}"/>"""
            }.joinToString("\n                    ")

            val opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookID" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>${escapeXml(book.title)}</dc:title>
                    <dc:creator id="author">${escapeXml(book.author)}</dc:creator>
                    <dc:language>en</dc:language>
                    <dc:identifier id="BookID">${book.id}</dc:identifier>
                    <dc:description>${escapeXml(book.synopsis)}</dc:description>
                    <meta property="dcterms:modified">2026-07-13T12:00:00Z</meta>
                    ${if (hasCover) "<meta name=\"cover\" content=\"cover-image\"/>" else ""}
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="style" href="style.css" media-type="text/css"/>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="about" href="about.xhtml" media-type="application/xhtml+xml"/>
                    ${if (hasCover) """<item id="cover-image" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                    <item id="cover-xhtml" href="cover.xhtml" media-type="application/xhtml+xml"/>""" else ""}
                    $manifestItems
                  </manifest>
                  <spine toc="ncx">
                    ${if (hasCover) "<itemref idref=\"cover-xhtml\"/>" else ""}
                    <itemref idref="about"/>
                    $spineRefs
                  </spine>
                </package>
            """.trimIndent()
            zipStream.write(opfXml.toByteArray(Charsets.UTF_8))
            zipStream.closeEntry()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try {
                zipStream?.close()
            } catch (ignored: IOException) {}
        }
    }

    /**
     * Compiles the novel into a high-quality PDF document using Android's native PdfDocument.
     */
    fun compilePdf(
        context: Context,
        book: BookEntity,
        chapters: List<ChapterEntity>,
        outputFile: File
    ): Boolean {
        if (chapters.isEmpty()) return false
        val pdfDocument = PdfDocument()

        val pageWidth = 595 // Standard A4 width in postscript points (1/72 inch)
        val pageHeight = 842 // Standard A4 height in postscript points
        val margin = 54f // 0.75 inch margins

        val textPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }

        val titlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subtitlePaint = TextPaint().apply {
            color = Color.DKGRAY
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val h2Paint = TextPaint().apply {
            color = Color.BLACK
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val contentWidth = (pageWidth - (margin * 2)).toInt()
        var pageCount = 1

        // 1. COVER PAGE
        val coverPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount++).create()
        val coverPage = pdfDocument.startPage(coverPageInfo)
        val canvas = coverPage.canvas

        val coverFile = File(outputFile.parentFile, "cover.jpg")
        val hasCover = coverFile.exists() && coverFile.length() > 0

        if (hasCover) {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeFile(coverFile.absolutePath)
                if (bitmap != null) {
                    // Center the cover image at the upper half of the page
                    val targetWidth = 260f
                    val scale = targetWidth / bitmap.width
                    val targetHeight = bitmap.height * scale
                    val left = margin + (contentWidth - targetWidth) / 2f
                    val top = margin + 40f
                    
                    canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + targetWidth, top + targetHeight), null)
                    bitmap.recycle()

                    // Title below the cover
                    val titleTop = top + targetHeight + 35f
                    val coverTitleLayout = StaticLayout.Builder.obtain(book.title, 0, book.title.length, titlePaint, contentWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .build()
                    canvas.save()
                    canvas.translate(margin, titleTop)
                    coverTitleLayout.draw(canvas)
                    canvas.restore()

                    // Author below title
                    val authorTop = titleTop + coverTitleLayout.height + 15f
                    val coverAuthorText = "Author: ${book.author}"
                    val coverAuthorLayout = StaticLayout.Builder.obtain(coverAuthorText, 0, coverAuthorText.length, subtitlePaint, contentWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .build()
                    canvas.save()
                    canvas.translate(margin, authorTop)
                    coverAuthorLayout.draw(canvas)
                    canvas.restore()
                } else {
                    drawDefaultCoverText(canvas, book, titlePaint, subtitlePaint, textPaint, margin, contentWidth, pageHeight)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                drawDefaultCoverText(canvas, book, titlePaint, subtitlePaint, textPaint, margin, contentWidth, pageHeight)
            }
        } else {
            drawDefaultCoverText(canvas, book, titlePaint, subtitlePaint, textPaint, margin, contentWidth, pageHeight)
        }

        pdfDocument.finishPage(coverPage)

        // 2. CHAPTER PAGES
        for (chapter in chapters) {
            var contentY = margin
            var startPage = true
            var activePage: PdfDocument.Page? = null
            var activeCanvas: Canvas? = null

            // Chapter Title
            val chapTitleLayout = StaticLayout.Builder.obtain(chapter.title, 0, chapter.title.length, h2Paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()

            // Chapter Content line wrapping
            val paragraphs = chapter.content.split("\n").filter { it.trim().isNotEmpty() }
            val textBuilder = StringBuilder()
            for (p in paragraphs) {
                textBuilder.append("    ").append(p.trim()).append("\n\n")
            }
            val contentText = textBuilder.toString()

            val contentLayout = StaticLayout.Builder.obtain(contentText, 0, contentText.length, textPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()

            var drawY = 0f
            val totalLines = contentLayout.lineCount

            while (drawY < contentLayout.height) {
                if (startPage) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount++).create()
                    activePage = pdfDocument.startPage(pageInfo)
                    activeCanvas = activePage.canvas
                    contentY = margin

                    // Draw Chapter Title on first page of chapter
                    if (drawY == 0f) {
                        activeCanvas.save()
                        activeCanvas.translate(margin, contentY)
                        chapTitleLayout.draw(activeCanvas)
                        activeCanvas.restore()
                        contentY += chapTitleLayout.height + 30f
                    }
                    startPage = false
                }

                // Calculate how much text fits on the remaining part of this page
                val maxAvailableHeight = pageHeight - margin - contentY
                var lineCountToDraw = 0
                var heightToDraw = 0f

                val startLine = contentLayout.getLineForVertical(drawY.toInt())
                var currentLine = startLine

                while (currentLine < totalLines) {
                    val lineBottom = contentLayout.getLineBottom(currentLine) - drawY
                    if (lineBottom <= maxAvailableHeight) {
                        heightToDraw = lineBottom
                        lineCountToDraw++
                        currentLine++
                    } else {
                        break
                    }
                }

                if (lineCountToDraw > 0 && activeCanvas != null) {
                    // Draw this block of lines
                    activeCanvas.save()
                    // Clip canvas strictly to heightToDraw to prevent next-page line bleed
                    activeCanvas.clipRect(margin, contentY, margin + contentWidth, contentY + heightToDraw)
                    // Draw lines starting offset by negative drawY
                    activeCanvas.translate(margin, contentY - drawY)
                    contentLayout.draw(activeCanvas)
                    activeCanvas.restore()

                    drawY += heightToDraw
                }

                // Finish active page
                if (activePage != null) {
                    pdfDocument.finishPage(activePage)
                }
                startPage = true // trigger new page creation for the next slice
            }
        }

        try {
            pdfDocument.writeTo(FileOutputStream(outputFile))
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawDefaultCoverText(
        canvas: Canvas,
        book: BookEntity,
        titlePaint: TextPaint,
        subtitlePaint: TextPaint,
        textPaint: TextPaint,
        margin: Float,
        contentWidth: Int,
        pageHeight: Int
    ) {
        // Title on cover
        val titleLayout = StaticLayout.Builder.obtain(book.title, 0, book.title.length, titlePaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        canvas.save()
        canvas.translate(margin, pageHeight / 4f)
        titleLayout.draw(canvas)
        canvas.restore()

        // Author on cover
        val authorText = "Author: ${book.author}"
        val authorLayout = StaticLayout.Builder.obtain(authorText, 0, authorText.length, subtitlePaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        canvas.save()
        canvas.translate(margin, (pageHeight / 4f) + titleLayout.height + 20)
        authorLayout.draw(canvas)
        canvas.restore()

        // Synopsis on cover
        val synHeader = "Synopsis"
        val synHeaderLayout = StaticLayout.Builder.obtain(synHeader, 0, synHeader.length, subtitlePaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        canvas.save()
        canvas.translate(margin, (pageHeight / 2f))
        synHeaderLayout.draw(canvas)
        canvas.restore()

        val synLayout = StaticLayout.Builder.obtain(book.synopsis, 0, book.synopsis.length, textPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        canvas.save()
        canvas.translate(margin, (pageHeight / 2f) + synHeaderLayout.height + 10)
        synLayout.draw(canvas)
        canvas.restore()
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun calculateCrc32(bytes: ByteArray): Long {
        val crc = java.util.zip.CRC32()
        crc.update(bytes)
        return crc.value
    }
}
