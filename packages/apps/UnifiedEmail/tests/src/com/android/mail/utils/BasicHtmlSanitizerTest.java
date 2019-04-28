package com.android.mail.utils;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * These test cases verify that each white listed element and attribute is accepted by the sanitizer
 * and everything else is correctly discarded.
 */
@SmallTest
public class BasicHtmlSanitizerTest extends AndroidTestCase {
    public void testAttributeDir() {
        sanitize("<div dir=\"ltr\">something</div>", "<div dir=\"ltr\">something</div>");
        sanitize("<div dir=\"rtl\">something</div>", "<div dir=\"rtl\">something</div>");
        sanitize("<div dir=\"LTR\">something</div>", "<div dir=\"ltr\">something</div>");
        sanitize("<div dir=\"RTL\">something</div>", "<div dir=\"rtl\">something</div>");
        sanitize("<div dir=\"blah\">something</div>", "<div>something</div>");
    }

    public void testA() {
        // allowed attributes
        sanitize("<a coords=\"something\"></a>", "<a coords=\"something\"></a>");
        sanitize("<a href=\"http://www.here.com\"></a>", "<a href=\"http://www.here.com\"></a>");
        sanitize("<a href=\"https://www.here.com\"></a>", "<a href=\"https://www.here.com\"></a>");
        sanitize("<a name=\"something\"></a>", "<a name=\"something\"></a>");
        sanitize("<a shape=\"something\"></a>", "<a shape=\"something\"></a>");

        // disallowed attributes (all links should launch a browser so we don't need these)
        sanitize("<a charset=\"something\"></a>", "");
        sanitize("<a datafld=\"something\"></a>", "");
        sanitize("<a datasrc=\"something\"></a>", "");
        sanitize("<a download=\"something\"></a>", "");
        sanitize("<a href=\"javascript:badness()\"></a>", "");
        sanitize("<a href=\"cid:ii_hyw5v8ej0\"></a>", "");
        sanitize("<a hreflang=\"something\"></a>", "");
        sanitize("<a media=\"something\"></a>", "");
        sanitize("<a methods=\"something\"></a>", "");
        sanitize("<a ping=\"something\"></a>", "");
        sanitize("<a rel=\"something\"></a>", "");
        sanitize("<a rev=\"something\"></a>", "");
        sanitize("<a target=\"_top\"></a>", "");
        sanitize("<a type=\"_top\"></a>", "");
        sanitize("<a urn=\"_top\"></a>", "");
        sanitize("<a onmouseout=\"alert(document.cookie)\">xxs link</a>", "xxs link");
        sanitize("<a onmouseover=\"alert(document.cookie)\">xxs link</a>", "xxs link");
        sanitize("<a onmouseover=alert(document.cookie)>xxs link</a>", "xxs link");
        sanitize("exp/*<a style='no\\xss:noxss(\"*//*\");\n" +
                "xss:ex/*XSS*//*/*/pression(alert(\"XSS\"))'>", "exp/*");
    }

    public void testAbbr() {
        sanitize("<abbr title=\"United Kingdom\">UK</abbr>",
                "<abbr title=\"United Kingdom\">UK</abbr>");
    }

    public void testAcronym() {
        sanitize("<acronym title=\"World Wide Web\">WWW</acronym>",
                "<acronym title=\"World Wide Web\">WWW</acronym>");
    }

    public void testAddress() {
        sanitize("<address>something</address>", "<address>something</address>");
    }

    public void testApplet() {
        // todo Gmail would also strip "malicious applet" as well... is this a problem?
        sanitize("<applet>malicious applet</applet>", "malicious applet");
    }

    public void testArea() {
        // allowed attributes
        sanitize("<area alt=\"something\"/>", "<area alt=\"something\" />");
        sanitize("<area coords=\"something\"/>", "<area coords=\"something\" />");
        sanitize("<area href=\"http://www.here.com\"/>", "<area href=\"http://www.here.com\" />");
        sanitize("<area href=\"https://www.here.com\"/>", "<area href=\"https://www.here.com\" />");
        sanitize("<area name=\"something\"/>", "<area name=\"something\" />");
        sanitize("<area nohref />", "<area nohref=\"nohref\" />");
        sanitize("<area shape=\"something\"/>", "<area shape=\"something\" />");

        // disallowed attributes (all links launch a browser so we don't need these attributes)
        sanitize("<area accessKey=\"A\"/>", "<area />");
        sanitize("<area download=\"something\"/>", "<area />");
        sanitize("<area href=\"javascript:badness()\"/>", "<area />");
        sanitize("<area href=\"cid:ii_hyw5v8ej0\"/>", "<area />");
        sanitize("<area hreflang=\"something\"/>", "<area />");
        sanitize("<area media=\"something\"/>", "<area />");
        sanitize("<area rel=\"something\"/>", "<area />");
        sanitize("<area target=\"something\"/>", "<area />");
        sanitize("<area tabindex=\"something\"/>", "<area />");
        sanitize("<area type=\"something\"/>", "<area />");
    }

    public void testArticle() {
        sanitize("<article></article>", "<article></article>");
    }

    public void testAside() {
        sanitize("<aside></aside>", "<aside></aside>");
    }

    public void testAudio() {
        sanitize("<audio>not supported</audio>", "not supported");
    }

    public void testB() {
        sanitize("<b>something</b>", "<b>something</b>");
    }

    public void testBase() {
        // allowed attributes
        sanitize("<base href=\"http://www.example.com/\">",
                "<base href=\"http://www.example.com/\" />");
        sanitize("<base href=\"https://www.example.com/\">",
                "<base href=\"https://www.example.com/\" />");

        // disallowed attributes
        sanitize("<base target=\"_blank\">", "<base />");
        sanitize("<base href=\"javascript:badness()\">", "<base />");
        sanitize("<base href=\"cid:ii_hyw5v8ej0\">", "<base />");
        sanitize("<base href=\"javascript:alert('XSS');//\">", "<base />");
    }

    public void testBasefont() {
        sanitize("<basefont color=\"something\"/>", "");
        sanitize("<basefont face=\"something\"/>", "");
        sanitize("<basefont size=\"something\"/>", "");
    }

    public void testBdi() {
        sanitize("<bdi>something</bdi>", "<bdi>something</bdi>");
        sanitize("<bdi dir=\"ltr\">something</bdi>", "<bdi dir=\"ltr\">something</bdi>");
    }

    public void testBdo() {
        sanitize("<bdo>something</bdo>", "<bdo>something</bdo>");
        sanitize("<bdo dir=\"ltr\">something</bdo>", "<bdo dir=\"ltr\">something</bdo>");
    }

    public void testBgsound() {
        sanitize("<bgsound src=\"sound1.mid\">", "");
        sanitize("<bgsound src=\"javascript:alert('XSS');\">", "");
    }

    public void testBig() {
        sanitize("<big>something</big>", "<big>something</big>");
    }

    public void testBlink() {
        sanitize("<blink>something</blink>", "something");
    }

    public void testBlockquote() {
        sanitize("<blockquote>something</blockquote>", "<blockquote>something</blockquote>");
        sanitize("<blockquote cite=\"http://www.here.com\">something</blockquote>",
                "<blockquote cite=\"http://www.here.com\">something</blockquote>");
        sanitize("<blockquote cite=\"javascript:badness()\">something</blockquote>",
                "<blockquote>something</blockquote>");

        sanitize("<blockquote style=\"margin:0;margin-left:0.8ex;border-left:1px #ccc solid;" +
                "padding-left:1ex\">",
                "<blockquote style=\"margin:0;margin-left:0.8ex;border-left:1px #ccc solid;" +
                        "padding-left:1ex\"></blockquote>");
    }

    /**
     * The body tag will be supplied by code that wraps this email with other formatting logic.
     * So, any body tags appearing within the email are translated to div tags.
     */
    public void testBody() {
        sanitize("<body alink=\"something\"></body>", "<div></div>");
        sanitize("<body background=\"something\"></body>", "<div></div>");
        sanitize("<body bgcolor=\"something\"></body>", "<div></div>");
        sanitize("<body link=\"something\"></body>", "<div></div>");
        sanitize("<body text=\"something\"></body>", "<div></div>");
        sanitize("<body vlink=\"something\"></body>", "<div></div>");

        // take extra care to ensure that these scripting callbacks don't survive
        sanitize("<body onafterprint=\"something\"></body>", "<div></div>");
        sanitize("<body onbeforeprint=\"something\"></body>", "<div></div>");
        sanitize("<body onbeforeunload=\"something\"></body>", "<div></div>");
        sanitize("<body onblur=\"something\"></body>", "<div></div>");
        sanitize("<body onerror=\"something\"></body>", "<div></div>");
        sanitize("<body onfocus=\"something\"></body>", "<div></div>");
        sanitize("<body onhashchange=\"something\"></body>", "<div></div>");
        sanitize("<body onload=\"something\"></body>", "<div></div>");
        sanitize("<body onmessage=\"something\"></body>", "<div></div>");
        sanitize("<body onoffline=\"something\"></body>", "<div></div>");
        sanitize("<body ononline=\"something\"></body>", "<div></div>");
        sanitize("<body onpopstate=\"something\"></body>", "<div></div>");
        sanitize("<body onredo=\"something\"></body>", "<div></div>");
        sanitize("<body onresize=\"something\"></body>", "<div></div>");
        sanitize("<body onstorage=\"something\"></body>", "<div></div>");
        sanitize("<body onundo=\"something\"></body>", "<div></div>");
        sanitize("<body onunload=\"something\"></body>", "<div></div>");
        sanitize("<body onload!#$%&()*~+-_.,:;?@[/|\\]^`=alert(\"XSS\")>", "<div></div>");
        sanitize("<body background=\"javascript:alert('XSS')\">", "<div></div>");
        sanitize("<body onload=alert('XSS')>", "<div></div>");
        sanitize("<body onload =alert('XSS')>", "<div></div>");
    }

    public void testBr() {
        sanitize("something<br>something", "something<br />something");
        sanitize("something<br clear=\"all\">something", "something<br clear=\"all\" />something");
    }

    public void testButton() {
        sanitize("<button>Click Me!</button>", "<button>Click Me!</button>");
        sanitize("<button autofocus=\"true\">Click Me!</button>",
                "<button autofocus=\"true\">Click Me!</button>");
        sanitize("<button disabled=\"true\">Click Me!</button>",
                "<button disabled=\"true\">Click Me!</button>");
        sanitize("<button formenctype=\"text/plain\">Click Me!</button>",
                "<button formenctype=\"text/plain\">Click Me!</button>");
        sanitize("<button formmethod=\"post\">Click Me!</button>",
                "<button formmethod=\"post\">Click Me!</button>");
        sanitize("<button formnovalidate=\"formnovalidate\">Click Me!</button>",
                "<button formnovalidate=\"formnovalidate\">Click Me!</button>");
        sanitize("<button formtarget=\"_top\">Click Me!</button>",
                "<button formtarget=\"_top\">Click Me!</button>");
        sanitize("<button name=\"something\">Click Me!</button>",
                "<button name=\"something\">Click Me!</button>");
        sanitize("<button type=\"button\">Click Me!</button>",
                "<button type=\"button\">Click Me!</button>");
        sanitize("<button value=\"something\">Click Me!</button>",
                "<button value=\"something\">Click Me!</button>");
        sanitize("<button formaction=\"http://www.overhere.com/\">Click Me!</button>",
                "<button formaction=\"http://www.overhere.com/\">Click Me!</button>");

        sanitize("<button formaction=\"javascript:badness()\">Click Me!</button>",
                "<button>Click Me!</button>");
    }

    public void testCanvas() {
        sanitize("<canvas></canvas>", "<canvas></canvas>");
        sanitize("<canvas width=\"500\"></canvas>", "<canvas width=\"500\"></canvas>");
        sanitize("<canvas height=\"500\"></canvas>", "<canvas height=\"500\"></canvas>");
    }

    public void testCaption() {
        sanitize("<caption>something</caption>", "<caption>something</caption>");
        sanitize("<caption align=\"left\">something</caption>",
                "<caption align=\"left\">something</caption>");
    }

    public void testCenter() {
        sanitize("<center>something</center>", "<center>something</center>");
    }

    public void testCite() {
        sanitize("<cite>something</cite>", "<cite>something</cite>");
    }

    public void testCode() {
        sanitize("<code>something</code>", "<code>something</code>");
    }

    public void testCol() {
        sanitize("<col>", "<col />");
        sanitize("<col align=\"left\">", "<col align=\"left\" />");
        sanitize("<col bgcolor=\"something\">", "<col bgcolor=\"something\" />");
        sanitize("<col char=\"something\">", "<col char=\"something\" />");
        sanitize("<col charoff=\"something\">", "<col charoff=\"something\" />");
        sanitize("<col span=\"something\">", "<col span=\"something\" />");
        sanitize("<col valign=\"something\">", "<col valign=\"something\" />");
        sanitize("<col width=\"something\">", "<col width=\"something\" />");
    }

    public void testColgroup() {
        sanitize("<colgroup></colgroup>", "<colgroup></colgroup>");
        sanitize("<colgroup align=\"left\"></colgroup>", "<colgroup align=\"left\"></colgroup>");
        sanitize("<colgroup char=\"something\"></colgroup>",
                "<colgroup char=\"something\"></colgroup>");
        sanitize("<colgroup charoff=\"something\"></colgroup>",
                "<colgroup charoff=\"something\"></colgroup>");
        sanitize("<colgroup span=\"something\"></colgroup>",
                "<colgroup span=\"something\"></colgroup>");
        sanitize("<colgroup valign=\"something\"></colgroup>",
                "<colgroup valign=\"something\"></colgroup>");
        sanitize("<colgroup width=\"something\"></colgroup>",
                "<colgroup width=\"something\"></colgroup>");
    }

    public void testDatalist() {
        sanitize("<datalist></datalist>", "<datalist></datalist>");
    }

    public void testDd() {
        sanitize("<dd>something</dd>", "<dd>something</dd>");
    }

    public void testDel() {
        sanitize("<del>something</del>", "<del>something</del>");
        sanitize("<del cite=\"javascript:badness();\">something</del>", "<del>something</del>");
        sanitize("<del cite=\"http://www.reason.com/\">something</del>",
                "<del cite=\"http://www.reason.com/\">something</del>");
        sanitize("<del datetime=\"something\">something</del>",
                "<del datetime=\"something\">something</del>");
    }

    public void testDetails() {
        sanitize("<details>something</details>", "<details>something</details>");
    }

    public void testDfn() {
        sanitize("<dfn>something</dfn>", "<dfn>something</dfn>");
    }

    public void testDialog() {
        sanitize("<dialog open>This is an open dialog window</dialog>",
                "This is an open dialog window");
    }

    public void testDir() {
        sanitize("<dir><li>something</li></dir>", "<dir><li>something</li></dir>");
        sanitize("<dir compact=\"compact\"><li>something</li></dir>",
                "<dir compact=\"compact\"><li>something</li></dir>");
    }

    public void testDiv() {
        sanitize("<div></div>", "<div></div>");
        sanitize("<div align=\"left\"></div>", "<div align=\"left\"></div>");
        sanitize("<div background=\"http://www.random.com/mypng.gif\"></div>",
                "<div background=\"http://www.random.com/mypng.gif\"></div>");

        sanitize("<div background=\"javascript:badness();\"></div>", "<div></div>");
        sanitize("<div style=\"width: expression(alert('XSS'));\">", "<div></div>");
        sanitize("<div style=\"background-image: url(javascript:alert('XSS'))\">", "<div></div>");
        sanitize("<div style=\"background-image:\\0075\\0072\\006C\\0028'\\006a\\0061\\0076\\0061" +
                "\\0073\\0063\\0072\\0069\\0070\\0074\\003a\\0061\\006c\\0065\\0072\\0074" +
                "\\0028.1027\\0058.1053\\0053\\0027\\0029'\\0029\">", "<div></div>");
        sanitize("<div style=\"background-image: url(&#1;javascript:alert('XSS'))\">",
                "<div></div>");
    }

    public void testDl() {
        sanitize("<dl></dl>", "<dl></dl>");
    }

    public void testDt() {
        sanitize("<dt></dt>", "<dt></dt>");
    }

    public void testEm() {
        sanitize("<em>something</em>", "<em>something</em>");
    }

    public void testEmbed() {
        sanitize("<embed src=\"helloworld.swf\">", "");
    }

    public void testFieldset() {
        sanitize("<fieldset>something</fieldset>", "<fieldset>something</fieldset>");
        sanitize("<fieldset disabled=\"true\">something</fieldset>",
                "<fieldset disabled=\"true\">something</fieldset>");
        sanitize("<fieldset form=\"formId\">something</fieldset>",
                "<fieldset form=\"formId\">something</fieldset>");
        sanitize("<fieldset name=\"something\">something</fieldset>",
                "<fieldset name=\"something\">something</fieldset>");
    }

    public void testFigcaption() {
        sanitize("<figcaption>Fig1. something</figcaption>",
                "<figcaption>Fig1. something</figcaption>");
    }

    public void testFigure() {
        sanitize("<figure>something</figure>", "<figure>something</figure>");
    }

    public void testFont() {
        sanitize("<font>something</font>", "something");
        sanitize("<font size=\"3\">something</font>", "<font size=\"3\">something</font>");
        sanitize("<font face=\"verdana\">something</font>",
                "<font face=\"verdana\">something</font>");
        sanitize("<font color=\"red\">something</font>", "<font color=\"red\">something</font>");
    }

    public void testFooter() {
        sanitize("<footer>something</footer>", "<footer>something</footer>");
    }

    public void testForm() {
        sanitize("<form></form>", "<form></form>");
        sanitize("<form accept=\"something\"></form>", "<form accept=\"something\"></form>");
        sanitize("<form accept-charset=\"something\"></form>",
                "<form accept-charset=\"something\"></form>");
        sanitize("<form autocomplete=\"on\"></form>", "<form autocomplete=\"on\"></form>");
        sanitize("<form enctype=\"text/plain\"></form>", "<form enctype=\"text/plain\"></form>");
        sanitize("<form method=\"get\"></form>", "<form method=\"get\"></form>");
        sanitize("<form name=\"something\"></form>", "<form name=\"something\"></form>");
        sanitize("<form novalidate=\"novalidate\"></form>",
                "<form novalidate=\"novalidate\"></form>");
        sanitize("<form target=\"_top\"></form>", "<form target=\"_top\"></form>");
        sanitize("<form action=\"http://www.overhere.com/\"></form>",
                "<form action=\"http://www.overhere.com/\"></form>");

        sanitize("<form action=\"javascript:badness()\"></form>", "<form></form>");
        sanitize("<form onsubmit=\"javascript:badness()\"></form>", "<form></form>");
        sanitize("<form onreset=\"javascript:badness()\"></form>", "<form></form>");
    }

    public void testFrame() {
        sanitize("<frame src=\"frame_a.htm\">", "");
    }

    public void testFrameset() {
        sanitize("<frameset cols=\"25%,*,25%\"></frameset>", "");
        sanitize("<frameset><frame src=\"javascript:alert('XSS');\"></frameset>", "");
    }

    public void testHead() {
        sanitize("<head></head>", "");
        sanitize("<head profile=\"http://www.overhere.com/\"></head>", "");
        sanitize("<head profile=\"javascript:badness()\"></head>", "");
    }

    public void testHeader() {
        sanitize("<header></header>", "<header></header>");
    }

    public void testH1() {
        sanitize("<h1>something</h1>", "<h1>something</h1>");
        sanitize("<h1 align=\"left\">something</h1>", "<h1 align=\"left\">something</h1>");
    }

    public void testH2() {
        sanitize("<h2>something</h2>", "<h2>something</h2>");
        sanitize("<h2 align=\"left\">something</h2>", "<h2 align=\"left\">something</h2>");
    }

    public void testH3() {
        sanitize("<h3>something</h3>", "<h3>something</h3>");
        sanitize("<h3 align=\"left\">something</h3>", "<h3 align=\"left\">something</h3>");
    }

    public void testH4() {
        sanitize("<h4>something</h4>", "<h4>something</h4>");
        sanitize("<h4 align=\"left\">something</h4>", "<h4 align=\"left\">something</h4>");
    }

    public void testH5() {
        sanitize("<h5>something</h5>", "<h5>something</h5>");
        sanitize("<h5 align=\"left\">something</h5>", "<h5 align=\"left\">something</h5>");
    }

    public void testH6() {
        sanitize("<h6>something</h6>", "<h6>something</h6>");
        sanitize("<h6 align=\"left\">something</h6>", "<h6 align=\"left\">something</h6>");
    }

    public void testHr() {
        sanitize("<hr/>", "<hr />");
        sanitize("<hr align=\"left\"/>", "<hr align=\"left\" />");
        sanitize("<hr noshade=\"noshade\"/>", "<hr noshade=\"noshade\" />");
        sanitize("<hr size=\"11\"/>", "<hr size=\"11\" />");
        sanitize("<hr width=\"666\"/>", "<hr width=\"666\" />");
    }

    public void testHtml() {
        sanitize("<html></html>", "");
        sanitize("<html xmlns=\"http://www.w3.org/1999/xhtml\"></html>", "");
        sanitize("<html manifest=\"http://www.overhere.com/\"></html>", "");
        sanitize("<html manifest=\"javascript:badness()\"></html>", "");
    }

    public void testI() {
        sanitize("<i></i>", "<i></i>");
    }

    public void testIframe() {
        sanitize("<iframe src=\"http://www.w3schools.com\"></iframe>", "");
        sanitize("<iframe src=http://ha.ckers.org/scriptlet.html <", "");
        sanitize("<iframe src=\"javascript:alert('XSS');\"></iframe>", "");
        sanitize("<iframe src=# onmouseover=\"alert(document.cookie)\"></iframe>", "");
    }

    public void testIsindex() {
        sanitize("<isindex prompt=\"Search Document... \"/>", "");
    }

    public void testImg() {
        sanitize("<img/>", "");
        sanitize("<img align=\"left\"/>", "<img align=\"left\" />");
        sanitize("<img alt=\"something\"/>", "<img alt=\"something\" />");
        sanitize("<img border=\"22\"/>", "<img border=\"22\" />");
        sanitize("<img crossorigin=\"anonymous \"/>", "<img crossorigin=\"anonymous \" />");
        sanitize("<img height=\"22\"/>", "<img height=\"22\" />");
        sanitize("<img hspace=\"22\"/>", "<img hspace=\"22\" />");
        sanitize("<img ismap=\"ismap\"/>", "<img ismap=\"ismap\" />");
        sanitize("<img usemap=\"something\"/>", "<img usemap=\"something\" />");
        sanitize("<img vspace=\"22\"/>", "<img vspace=\"22\" />");
        sanitize("<img width=\"22\"/>", "<img width=\"22\" />");
        sanitize("<img src=\"http://www.overhere.com/\"></img>",
                "<img src=\"http://www.overhere.com/\" />");
        sanitize("<img src=\"https://www.overhere.com/\"></img>",
                "<img src=\"https://www.overhere.com/\" />");
        sanitize("<img src=\"cid:ii_hyw5v8ej0\"></img>",
                "<img src=\"cid:ii_hyw5v8ej0\" />");
        sanitize("<img longdesc=\"http://www.overhere.com/\"></img>",
                "<img longdesc=\"http://www.overhere.com/\" />");
        sanitize("<img longdesc=\"https://www.overhere.com/\"></img>",
                "<img longdesc=\"https://www.overhere.com/\" />");

        sanitize("<img src=\"javascript:badness()\"></img>", "");
        sanitize("<img longdesc=\"javascript:badness()\"></img>", "");
        sanitize("<img longdesc=\"cid:ii_hyw5v8ej0\"></img>", "");
        sanitize("<img src=javascript:alert('XSS')>", "");
        sanitize("<img src=JaVaScRiPt:alert('XSS')>", "");
        sanitize("<img src=javascript:alert(\"XSS\")>", "");
        sanitize("<img src=`javascript:alert(\"RSnake says, 'XSS'\")`>", "");
        sanitize("<img \"\"\"><script>alert(\"XSS\")</script>\">", "&#34;&gt;");
        sanitize("<img src=# onmouseover=\"alert('xxs')\">", "<img src=\"#\" />");
        sanitize("<img src= onmouseover=\"alert('xxs')\">", "<img src=\"onmouseover&#61;\" />");
        sanitize("<img onmouseover=\"alert('xxs')\">", "");
        sanitize("<img src=/ onerror=\"alert(String.fromCharCode(88,83,83))\"></img>",
                "<img src=\"/\" />");
        sanitize("<img src=&#106;&#97;&#118;&#97;&#115;&#99;&#114;&#105;&#112;&#116;&#58;&#97;" +
                "&#108;&#101;&#114;&#116;&#40;\n&#39;&#88;&#83;&#83;&#39;&#41;>",
                "");
        sanitize("<img src=&#0000106&#0000097&#0000118&#0000097&#0000115&#0000099&#0000114" +
                "&#0000105&#0000112&#0000116&#0000058&#0000097&\n" +
                "#0000108&#0000101&#0000114&#0000116&#0000040&#0000039&#0000088&#0000083&#0000083" +
                "&#0000039&#0000041>", "");
        sanitize("<img src=&#x6A&#x61&#x76&#x61&#x73&#x63&#x72&#x69&#x70&#x74&#x3A&#x61&#x6C&#x65" +
                "&#x72&#x74&#x28&#x27&#x58&#x53&#x53&#x27&#x29>", "");
        sanitize("<img src=\"jav\tascript:alert('XSS');\">", "");
        sanitize("<img src=\"jav&#x09;ascript:alert('XSS');\">", "");
        sanitize("<img src=\"jav&#x0A;ascript:alert('XSS');\">", "");
        sanitize("<img src=\"jav&#x0D;ascript:alert('XSS');\">", "");
        sanitize("<img src=java\0script:alert(\\\"XSS\\\")>", "");
        sanitize("<img src=\" &#14;  javascript:alert('XSS');\">", "");
        sanitize("<img src=\"javascript:alert('XSS')\"", "");
        sanitize("<img dynsrc=\"javascript:alert('XSS')\">", "");
        sanitize("<img lowsrc=\"javascript:alert('XSS')\">", "");
        sanitize("<img src='vbscript:msgbox(\"XSS\")'>", "");
        sanitize("<img src=\"livescript:[code]\">", "");
        sanitize("<img style=\"xss:expr/*XSS*/ession(alert('XSS'))\">", "");
    }

    public void testInput() {
        sanitize("<input accept=\"image/*\"/>", "<input accept=\"image/*\" />");
        sanitize("<input align=\"left\"/>", "<input align=\"left\" />");
        sanitize("<input alt=\"something\"/>", "<input alt=\"something\" />");
        sanitize("<input autocomplete=\"on\"/>", "<input autocomplete=\"on\" />");
        sanitize("<input autofocus=\"autofocus\"/>", "<input autofocus=\"autofocus\" />");
        sanitize("<input checked=\"checked\"/>", "<input checked=\"checked\" />");
        sanitize("<input disabled=\"disabled\"/>", "<input disabled=\"disabled\" />");
        sanitize("<input form=\"1\"/>", "<input form=\"1\" />");
        sanitize("<input formenctype=\"text/plain\"/>", "<input formenctype=\"text/plain\" />");
        sanitize("<input formmethod=\"post\"/>", "<input formmethod=\"post\" />");
        sanitize("<input formnovalidate=\"formnovalidate\"/>",
                "<input formnovalidate=\"formnovalidate\" />");
        sanitize("<input formtarget=\"_top\"/>", "<input formtarget=\"_top\" />");
        sanitize("<input height=\"22\"/>", "<input height=\"22\" />");
        sanitize("<input list=\"something\"/>", "<input list=\"something\" />");
        sanitize("<input max=\"1000\"/>", "<input max=\"1000\" />");
        sanitize("<input maxlength=\"10\"/>", "<input maxlength=\"10\" />");
        sanitize("<input min=\"10\"/>", "<input min=\"10\" />");
        sanitize("<input multiple=\"multiple\"/>", "<input multiple=\"multiple\" />");
        sanitize("<input name=\"herman\"/>", "<input name=\"herman\" />");
        sanitize("<input pattern=\"*.*\"/>", "<input pattern=\"*.*\" />");
        sanitize("<input placeholder=\"something\"/>", "<input placeholder=\"something\" />");
        sanitize("<input readonly=\"readonly\"/>", "<input readonly=\"readonly\" />");
        sanitize("<input required=\"required\"/>", "<input required=\"required\" />");
        sanitize("<input size=\"22\"/>", "<input size=\"22\" />");
        sanitize("<input step=\"5\"/>", "<input step=\"5\" />");
        sanitize("<input type=\"button\"/>", "<input type=\"button\" />");
        sanitize("<input value=\"something\"/>", "<input value=\"something\" />");
        sanitize("<input width=\"50\"/>", "<input width=\"50\" />");
        sanitize("<input src=\"http://www.overhere.com/\"></input>",
                "<input src=\"http://www.overhere.com/\" />");
        sanitize("<input src=\"https://www.overhere.com/\"></input>",
                "<input src=\"https://www.overhere.com/\" />");
        sanitize("<input formaction=\"http://www.overhere.com/\"></input>",
                "<input formaction=\"http://www.overhere.com/\" />");
        sanitize("<input formaction=\"https://www.overhere.com/\"></input>",
                "<input formaction=\"https://www.overhere.com/\" />");

        sanitize("<input src=\"cid:ii_hyw5v8ej0\"></input>", "");
        sanitize("<input src=\"javascript:badness()\"></input>", "");
        sanitize("<input formaction=\"cid:ii_hyw5v8ej0\"></input>", "");
        sanitize("<input formaction=\"javascript:badness()\"></input>", "");
        sanitize("<input type=\"image\" src=\"javascript:alert('XSS');\">",
                "<input type=\"image\" />");
        sanitize("<input type=\"text\" onchange=\"javascript:alert('XSS');\">",
                "<input type=\"text\" />");
        sanitize("<input type=\"button\" onclick=\"javascript:alert('XSS');\">",
                "<input type=\"button\" />");
        sanitize("<input type=\"button\" ondblclick=\"javascript:alert('XSS');\">",
                "<input type=\"button\" />");
    }

    public void testIns() {
        sanitize("<ins>something</ins>", "<ins>something</ins>");
        sanitize("<ins cite=\"javascript:badness();\">something</ins>", "<ins>something</ins>");
        sanitize("<ins cite=\"http://www.reason.com/\">something</ins>",
                "<ins cite=\"http://www.reason.com/\">something</ins>");
        sanitize("<ins cite=\"https://www.reason.com/\">something</ins>",
                "<ins cite=\"https://www.reason.com/\">something</ins>");
        sanitize("<ins datetime=\"something\">something</ins>",
                "<ins datetime=\"something\">something</ins>");

        sanitize("<ins cite=\"cid:ii_hyw5v8ej0\">something</ins>",
                "<ins>something</ins>");
    }

    public void testKbd() {
        sanitize("<kbd>something</kbd>", "<kbd>something</kbd>");
    }

    public void testKeygen() {
        sanitize("<keygen/>", "<keygen />");
        sanitize("<keygen autofocus=\"autofocus\"/>", "<keygen autofocus=\"autofocus\" />");
        sanitize("<keygen challenge=\"challenge\"/>", "<keygen challenge=\"challenge\" />");
        sanitize("<keygen disabled=\"disabled\"/>", "<keygen disabled=\"disabled\" />");
        sanitize("<keygen form=\"formId\"/>", "<keygen form=\"formId\" />");
        sanitize("<keygen keytype=\"rsa\"/>", "<keygen keytype=\"rsa\" />");
        sanitize("<keygen name=\"herman\"/>", "<keygen name=\"herman\" />");
    }

    public void testLabel() {
        sanitize("<label for=\"elementId\">Something:</label>", "<label>Something:</label>");
        sanitize("<label form=\"formId\">Something:</label>",
                "<label form=\"formId\">Something:</label>");
    }

    public void testLegend() {
        sanitize("<legend>Something:</legend>", "<legend>Something:</legend>");
        sanitize("<legend align=\"left\">Something:</legend>",
                "<legend align=\"left\">Something:</legend>");
    }

    public void testLi() {
        sanitize("<li>Something:</li>", "<li>Something:</li>");
        sanitize("<li type=\"a\">Something:</li>", "<li type=\"a\">Something:</li>");
        sanitize("<li value=\"11\">Something:</li>", "<li value=\"11\">Something:</li>");
    }

    public void testLink() {
        sanitize("<link charset=\"utf8\"/>", "");
        sanitize("<link href=\"http://www.reason.com/\"/>", "");
        sanitize("<link href=\"https://www.reason.com/\"/>", "");
        sanitize("<link href=\"cid:ii_hyw5v8ej0\"/>", "");
        sanitize("<link hreflang=\"fr_CA\"/>", "");
        sanitize("<link media=\"tv\"/>", "");
        sanitize("<link rel=\"alternate\"/>", "");
        sanitize("<link rev=\"something\"/>", "");
        sanitize("<link sizes=\"500x400\"/>", "");
        sanitize("<link target=\"_top\"/>", "");
        sanitize("<link type=\"mimeType\"/>", "");
        sanitize("<link href=\"javascript:alert('XSS');\">", "");
    }

    public void testMain() {
        sanitize("<main>something</main>", "<main>something</main>");
    }

    public void testMap() {
        sanitize("<map></map>", "<map></map>");
        sanitize("<map name=\"mapname\"></map>", "<map name=\"mapname\"></map>");
    }

    public void testMark() {
        sanitize("<mark>something</mark>", "<mark>something</mark>");
    }

    public void testMenu() {
        sanitize("<menu></menu>", "<menu></menu>");
        sanitize("<menu label=\"Edit\"></menu>", "<menu label=\"Edit\"></menu>");
        sanitize("<menu type=\"popup\"></menu>", "<menu type=\"popup\"></menu>");
    }

    public void testMenuitem() {
        sanitize("<menuitem></menuitem>", "<menuitem></menuitem>");
        sanitize("<menuitem checked=\"checked\"></menuitem>",
                "<menuitem checked=\"checked\"></menuitem>");
        sanitize("<menuitem command=\"something\"></menuitem>",
                "<menuitem command=\"something\"></menuitem>");
        sanitize("<menuitem default=\"default\"></menuitem>",
                "<menuitem default=\"default\"></menuitem>");
        sanitize("<menuitem disabled=\"disabled\"></menuitem>",
                "<menuitem disabled=\"disabled\"></menuitem>");
        sanitize("<menuitem icon=\"http://www.reason.com/\"></menuitem>",
                "<menuitem icon=\"http://www.reason.com/\"></menuitem>");
        sanitize("<menuitem icon=\"https://www.reason.com/\"></menuitem>",
                "<menuitem icon=\"https://www.reason.com/\"></menuitem>");
        sanitize("<menuitem label=\"something\"></menuitem>",
                "<menuitem label=\"something\"></menuitem>");
        sanitize("<menuitem type=\"checkbox\"></menuitem>",
                "<menuitem type=\"checkbox\"></menuitem>");
        sanitize("<menuitem radiogroup=\"something\"></menuitem>",
                "<menuitem radiogroup=\"something\"></menuitem>");

        sanitize("<menuitem icon=\"cid:ii_hyw5v8ej0\"></menuitem>", "<menuitem></menuitem>");
        sanitize("<menuitem icon=\"javascript:badness()\"></menuitem>", "<menuitem></menuitem>");
    }

    public void testMeta() {
        sanitize("<meta/>", "");
        sanitize("<meta charset=\"utf8\" />", "");
        sanitize("<meta content=\"something\" />", "");
        sanitize("<meta http-equiv=\"refresh\" />", "");
        sanitize("<meta name=\"description\" />", "");
        sanitize("<meta scheme=\"YYYY-MM-DD\" />", "");
        sanitize("<meta http-equiv=\"Link\" content=\"<http://ha.ckers.org/xss.css>; " +
                "REL=stylesheet\">", "");
        sanitize("<meta http-equiv=\"refresh\" content=\"0;url=javascript:alert('XSS');\">", "");
        sanitize("<meta http-equiv=\"refresh\" content=\"0;url=data:text/html " +
                "base64,PHNjcmlwdD5hbGVydCgnWFNTJyk8L3NjcmlwdD4K\">", "");
        sanitize("<meta http-equiv=\"refresh\" CONTENT=\"0; url=http://;" +
                "URL=javascript:alert('XSS');\">", "");
    }

    public void testMeter() {
        sanitize("<meter>2 out of 10</meter>", "<meter>2 out of 10</meter>");
        sanitize("<meter form=\"formId\">2 out of 10</meter>",
                "<meter form=\"formId\">2 out of 10</meter>");
        sanitize("<meter high=\"10\">2 out of 10</meter>",
                "<meter high=\"10\">2 out of 10</meter>");
        sanitize("<meter low=\"10\">2 out of 10</meter>", "<meter low=\"10\">2 out of 10</meter>");
        sanitize("<meter max=\"10\">2 out of 10</meter>", "<meter max=\"10\">2 out of 10</meter>");
        sanitize("<meter min=\"10\">2 out of 10</meter>", "<meter min=\"10\">2 out of 10</meter>");
        sanitize("<meter optimum=\"10\">2 out of 10</meter>",
                "<meter optimum=\"10\">2 out of 10</meter>");
        sanitize("<meter value=\"10\">2 out of 10</meter>",
                "<meter value=\"10\">2 out of 10</meter>");
    }

    public void testNav() {
        sanitize("<nav>something</nav>", "<nav>something</nav>");
    }

    public void testNoframes() {
        sanitize("<noframes>No frames!</noframes>", "");
    }

    public void testNoscript() {
        sanitize("<noscript>No JavaScript!</noscript>", "");
    }

    public void testObject() {
        sanitize("<object>No Objects!</object>", "");
        sanitize("<object type=\"text/x-scriptlet\" data=\"http://ha.ckers.org/scriptlet.html\">" +
                "</object>", "");
    }

    public void testOl() {
        sanitize("<ol></ol>", "<ol></ol>");
        sanitize("<ol compact=\"compact\"></ol>", "<ol compact=\"compact\"></ol>");
        sanitize("<ol reversed=\"reversed\"></ol>", "<ol reversed=\"reversed\"></ol>");
        sanitize("<ol start=\"11\"></ol>", "<ol start=\"11\"></ol>");
        sanitize("<ol type=\"a\"></ol>", "<ol type=\"a\"></ol>");
    }

    public void testOptgroup() {
        sanitize("<optgroup></optgroup>", "<optgroup></optgroup>");
        sanitize("<optgroup disabled=\"disabled\"></optgroup>",
                "<optgroup disabled=\"disabled\"></optgroup>");
        sanitize("<optgroup label=\"something\"></optgroup>",
                "<optgroup label=\"something\"></optgroup>");
    }

    public void testOption() {
        sanitize("<option>something</option>", "<option>something</option>");
        sanitize("<option disabled=\"disabled\">something</option>",
                "<option disabled=\"disabled\">something</option>");
        sanitize("<option label=\"something\">something</option>",
                "<option label=\"something\">something</option>");
        sanitize("<option selected=\"selected\">something</option>",
                "<option selected=\"selected\">something</option>");
        sanitize("<option value=\"volvo\">something</option>",
                "<option value=\"volvo\">something</option>");
    }

    public void testOutput() {
        sanitize("<output></output>", "<output></output>");
        sanitize("<output for=\"elementId\"></output>", "<output></output>");
        sanitize("<output form=\"formId\"></output>", "<output form=\"formId\"></output>");
        sanitize("<output name=\"something\"></output>", "<output name=\"something\"></output>");
    }

    public void testP() {
        sanitize("<p>something</p>", "<p>something</p>");
        sanitize("<p align=\"left\">something</p>", "<p align=\"left\">something</p>");
    }

    public void testParam() {
        sanitize("<param name=\"autoplay\" value=\"true\">", "");
    }

    public void testPre() {
        sanitize("<pre>something</pre>", "<pre>something</pre>");
        sanitize("<pre width=\"400\">something</pre>", "<pre width=\"400\">something</pre>");
    }

    public void testProgress() {
        sanitize("<progress></progress>", "<progress></progress>");
        sanitize("<progress value=\"22\"></progress>", "<progress value=\"22\"></progress>");
        sanitize("<progress max=\"100\"></progress>", "<progress max=\"100\"></progress>");
    }

    public void testQ() {
        sanitize("<q>something</q>", "<q>something</q>");
        sanitize("<q cite=\"http://www.reason.com/\">something</q>",
                "<q cite=\"http://www.reason.com/\">something</q>");
        sanitize("<q cite=\"https://www.reason.com/\">something</q>",
                "<q cite=\"https://www.reason.com/\">something</q>");

        sanitize("<q cite=\"cid:ii_hyw5v8ej0\">something</q>", "<q>something</q>");
        sanitize("<q cite=\"javascript:badness()\">something</q>", "<q>something</q>");
    }

    public void testRp() {
        sanitize("<rp>something</rp>", "<rp>something</rp>");
    }

    public void testRt() {
        sanitize("<rt>something</rt>", "<rt>something</rt>");
    }

    public void testRuby() {
        sanitize("<ruby></ruby>", "<ruby></ruby>");
    }

    public void testS() {
        sanitize("<s>old skool strikethrough</s>", "<s>old skool strikethrough</s>");
    }

    public void testSamp() {
        sanitize("<samp>something</samp>", "<samp>something</samp>");
    }

    public void testScript() {
        sanitize("<script>malicious script</script>", "");
        sanitize("<<script>alert(\"XSS\");//<</script>", "&lt;");
        sanitize("<script src=http://ha.ckers.org/xss.js></script>", "");
        sanitize("<script/XSS src=\"http://ha.ckers.org/xss.js\"></script>", "");
        sanitize("<script/src=\"http://ha.ckers.org/xss.js\"></script>", "");
        sanitize("<script src=http://ha.ckers.org/xss.js?< B >", "");
        sanitize("<script src=//ha.ckers.org/.j>", "");
        sanitize("</title><script>alert(\"XSS\");</script>", "");
        sanitize("<script src=\"http://ha.ckers.org/xss.jpg\"></script>", "");

        String attack = "';alert(String.fromCharCode(88,83,83))//';" +
                "alert(String.fromCharCode(88,83,83))//\";\n" +
                "alert(String.fromCharCode(88,83,83))//\";" +
                "alert(String.fromCharCode(88,83,83))//--\n" +
                "></SCRIPT>\">'><SCRIPT>alert(String.fromCharCode(88,83,83))</SCRIPT>";
        String defend = "&#39;;alert(String.fromCharCode(88,83,83))//&#39;;" +
                "alert(String.fromCharCode(88,83,83))//&#34;;\n" +
                "alert(String.fromCharCode(88,83,83))//&#34;;" +
                "alert(String.fromCharCode(88,83,83))//--\n" +
                "&gt;&#34;&gt;&#39;&gt;";
        sanitize(attack, defend);
    }

    public void testSection() {
        sanitize("<section>something</section>", "<section>something</section>");
    }

    public void testSelect() {
        sanitize("<select></select>", "<select></select>");
        sanitize("<select autofocus=\"autofocus\"></select>",
                "<select autofocus=\"autofocus\"></select>");
        sanitize("<select disabled=\"disabled\"></select>",
                "<select disabled=\"disabled\"></select>");
        sanitize("<select form=\"formId\"></select>", "<select form=\"formId\"></select>");
        sanitize("<select multiple=\"multiple\"></select>",
                "<select multiple=\"multiple\"></select>");
        sanitize("<select required=\"required\"></select>",
                "<select required=\"required\"></select>");
        sanitize("<select size=\"11\"></select>", "<select size=\"11\"></select>");
    }

    public void testSmall() {
        sanitize("<small>something</small>", "<small>something</small>");
    }

    public void testSource() {
        sanitize("<source/>", "");
        sanitize("<source></source>", "");
    }

    public void testSpan() {
        sanitize("<span style=\"color:blue\">something</span>",
                "<span style=\"color:blue\">something</span>");
    }

    public void testStrike() {
        sanitize("<strike>something</strike>", "<strike>something</strike>");
    }

    public void testStrong() {
        sanitize("<strong>something</strong>", "<strong>something</strong>");
    }

    public void testStyle() {
        sanitize("<style>something</style>", "");
        sanitize("<style media=\"something\">something</style>", "");
        sanitize("<style scoped=\"scoped\">something</style>", "");
        sanitize("<style type=\"text/css\">something</style>", "");
        sanitize("<style>li {list-style-image: url(\"javascript:alert('XSS')\");}</style>", "");
        sanitize("<style>@im\\port'\\ja\\vasc\\ript:alert(\"XSS\")';</style>", "");
        sanitize("<style>body{-moz-binding:url(\"http://ha.ckers.org/xssmoz.xml#xss\")}</style>",
                "");
        sanitize("<style>@import'http://ha.ckers.org/xss.css';</style>", "");
        sanitize("<style type=\"text/javascript\">alert('XSS');</style>", "");
        sanitize("<style>.XSS{background-image:url(\"javascript:alert('XSS')\");}</style>" +
                "<a class=XSS></a>", "");
        sanitize("<style type=\"text/css\">body{background:url(\"javascript:alert('XSS')\")}" +
                "</style>", "");
    }

    public void testSub() {
        sanitize("<sub>something</sub>", "<sub>something</sub>");
    }

    public void testSummary() {
        sanitize("<summary>something</summary>", "<summary>something</summary>");
    }

    public void testSup() {
        sanitize("<sup>something</sup>", "<sup>something</sup>");
    }

    public void testTable() {
        sanitize("<table></table>", "<table></table>");
        sanitize("<table align=\"left\"></table>", "<table align=\"left\"></table>");
        sanitize("<table bgcolor=\"red\"></table>", "<table bgcolor=\"red\"></table>");
        sanitize("<table border=\"1\"></table>", "<table border=\"1\"></table>");
        sanitize("<table cellpadding=\"1\"></table>", "<table cellpadding=\"1\"></table>");
        sanitize("<table cellspacing=\"1\"></table>", "<table cellspacing=\"1\"></table>");
        sanitize("<table frame=\"void\"></table>", "<table frame=\"void\"></table>");
        sanitize("<table rules=\"none\"></table>", "<table rules=\"none\"></table>");
        sanitize("<table sortable=\"sortable\"></table>", "<table sortable=\"sortable\"></table>");
        sanitize("<table summary=\"something\"></table>", "<table summary=\"something\"></table>");
        sanitize("<table width=\"11\"></table>", "<table width=\"11\"></table>");

        sanitize("<table background=\"javascript:alert('XSS')\">", "<table></table>");
    }

    public void testTbody() {
        sanitize("<tbody></tbody>", "<tbody></tbody>");
        sanitize("<tbody char=\"something\"></tbody>", "<tbody char=\"something\"></tbody>");
        sanitize("<tbody charoff=\"11\"></tbody>", "<tbody charoff=\"11\"></tbody>");
        sanitize("<tbody valign=\"top\"></tbody>", "<tbody valign=\"top\"></tbody>");
    }

    public void testTd() {
        sanitize("<td></td>", "<td></td>");
        sanitize("<td abbr=\"something\"></td>", "<td abbr=\"something\"></td>");
        sanitize("<td align=\"left\"></td>", "<td align=\"left\"></td>");
        sanitize("<td axis=\"something\"></td>", "<td axis=\"something\"></td>");
        sanitize("<td bgcolor=\"red\"></td>", "<td bgcolor=\"red\"></td>");
        sanitize("<td char=\"something\"></td>", "<td char=\"something\"></td>");
        sanitize("<td charoff=\"22\"></td>", "<td charoff=\"22\"></td>");
        sanitize("<td colspan=\"33\"></td>", "<td colspan=\"33\"></td>");
        sanitize("<td height=\"44\"></td>", "<td height=\"44\"></td>");
        sanitize("<td nowrap=\"nowrap\"></td>", "<td nowrap=\"nowrap\"></td>");
        sanitize("<td rowspan=\"3\"></td>", "<td rowspan=\"3\"></td>");
        sanitize("<td scope=\"col\"></td>", "<td scope=\"col\"></td>");
        sanitize("<td valign=\"top\"></td>", "<td valign=\"top\"></td>");
        sanitize("<td width=\"55\"></td>", "<td width=\"55\"></td>");

        sanitize("<td headers=\"headerId\"></td>", "<td></td>");
        sanitize("<td background=\"javascript:alert('XSS')\">", "<td></td>");
    }

    public void testTextarea() {
        sanitize("<textarea></textarea>", "<textarea></textarea>");
        sanitize("<textarea autofocus=\"autofocus\"></textarea>",
                "<textarea autofocus=\"autofocus\"></textarea>");
        sanitize("<textarea cols=\"1\"></textarea>", "<textarea cols=\"1\"></textarea>");
        sanitize("<textarea disabled=\"disabled\"></textarea>",
                "<textarea disabled=\"disabled\"></textarea>");
        sanitize("<textarea form=\"formId\"></textarea>", "<textarea form=\"formId\"></textarea>");
        sanitize("<textarea maxlength=\"2\"></textarea>", "<textarea maxlength=\"2\"></textarea>");
        sanitize("<textarea name=\"something\"></textarea>",
                "<textarea name=\"something\"></textarea>");
        sanitize("<textarea placeholder=\"something\"></textarea>",
                "<textarea placeholder=\"something\"></textarea>");
        sanitize("<textarea readonly=\"readonly\"></textarea>",
                "<textarea readonly=\"readonly\"></textarea>");
        sanitize("<textarea required=\"required\"></textarea>",
                "<textarea required=\"required\"></textarea>");
        sanitize("<textarea rows=\"3\"></textarea>", "<textarea rows=\"3\"></textarea>");
        sanitize("<textarea wrap=\"soft\"></textarea>", "<textarea wrap=\"soft\"></textarea>");
    }

    public void testTfoot() {
        sanitize("<tfoot></tfoot>", "<tfoot></tfoot>");
        sanitize("<tfoot align=\"left\"></tfoot>", "<tfoot align=\"left\"></tfoot>");
        sanitize("<tfoot char=\"something\"></tfoot>", "<tfoot char=\"something\"></tfoot>");
        sanitize("<tfoot charoff=\"1\"></tfoot>", "<tfoot charoff=\"1\"></tfoot>");
        sanitize("<tfoot valign=\"top\"></tfoot>", "<tfoot valign=\"top\"></tfoot>");
    }

    public void testTh() {
        sanitize("<th></th>", "<th></th>");
        sanitize("<th abbr=\"something\"></th>", "<th abbr=\"something\"></th>");
        sanitize("<th align=\"left\"></th>", "<th align=\"left\"></th>");
        sanitize("<th axis=\"something\"></th>", "<th axis=\"something\"></th>");
        sanitize("<th bgcolor=\"red\"></th>", "<th bgcolor=\"red\"></th>");
        sanitize("<th char=\"something\"></th>", "<th char=\"something\"></th>");
        sanitize("<th charoff=\"22\"></th>", "<th charoff=\"22\"></th>");
        sanitize("<th colspan=\"33\"></th>", "<th colspan=\"33\"></th>");
        sanitize("<th height=\"44\"></th>", "<th height=\"44\"></th>");
        sanitize("<th nowrap=\"nowrap\"></th>", "<th nowrap=\"nowrap\"></th>");
        sanitize("<th rowspan=\"3\"></th>", "<th rowspan=\"3\"></th>");
        sanitize("<th scope=\"col\"></th>", "<th scope=\"col\"></th>");
        sanitize("<th sorted=\"reversed\"></th>", "<th sorted=\"reversed\"></th>");
        sanitize("<th valign=\"top\"></th>", "<th valign=\"top\"></th>");
        sanitize("<th width=\"55\"></th>", "<th width=\"55\"></th>");

        sanitize("<th headers=\"headerId\"></th>", "<th></th>");
    }

    public void testThead() {
        sanitize("<thead></thead>", "<thead></thead>");
        sanitize("<thead align=\"left\"></thead>", "<thead align=\"left\"></thead>");
        sanitize("<thead char=\"something\"></thead>", "<thead char=\"something\"></thead>");
        sanitize("<thead charoff=\"1\"></thead>", "<thead charoff=\"1\"></thead>");
        sanitize("<thead valign=\"top\"></thead>", "<thead valign=\"top\"></thead>");
    }

    public void testTime() {
        sanitize("<time></time>", "<time></time>");
        sanitize("<time datetime=\"datetime\"></time>", "<time datetime=\"datetime\"></time>");
    }

    public void testTitle() {
        sanitize("<title>something</title>", "");
    }

    public void testTr() {
        sanitize("<tr></tr>", "<tr></tr>");
        sanitize("<tr align=\"left\"></tr>", "<tr align=\"left\"></tr>");
        sanitize("<tr bgcolor=\"red\"></tr>", "<tr bgcolor=\"red\"></tr>");
        sanitize("<tr char=\"something\"></tr>", "<tr char=\"something\"></tr>");
        sanitize("<tr charoff=\"1\"></tr>", "<tr charoff=\"1\"></tr>");
        sanitize("<tr valign=\"top\"></tr>", "<tr valign=\"top\"></tr>");
    }

    public void testTrack() {
        sanitize("<track/>", "");
        sanitize("<track></track>", "");
    }

    public void testTt() {
        sanitize("<tt>something</tt>", "<tt>something</tt>");
    }

    public void testU() {
        sanitize("<u>something</u>", "<u>something</u>");
    }

    public void testUl() {
        sanitize("<ul compact=\"compact\"></ul>", "<ul compact=\"compact\"></ul>");
        sanitize("<ul type=\"disc\"></ul>", "<ul type=\"disc\"></ul>");
    }

    public void testVar() {
        sanitize("<var>something</var>", "<var>something</var>");
    }

    public void testVideo() {
        sanitize("<video></video>", "");
    }

    public void testWbr() {
        sanitize("word1<wbr/>word2", "word1<wbr />word2");
    }

    private void sanitize(String dirtyHTML, String expectedHTML) {
        final String cleansedHTML = HtmlSanitizer.sanitizeHtml(dirtyHTML);
        assertEquals(expectedHTML, cleansedHTML);
    }
}
