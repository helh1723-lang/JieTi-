# WebView calls these methods by their JavaScript-visible names.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# PdfBox-Android intentionally leaves this optional JPX codec out of its artifact.
# JPXFilter catches its absence; text extraction and Android PdfRenderer stay unchanged.
# Upstream: https://github.com/TomRoush/PdfBox-Android#reading-jpx-images
-dontwarn com.gemalto.jp2.JP2Decoder
