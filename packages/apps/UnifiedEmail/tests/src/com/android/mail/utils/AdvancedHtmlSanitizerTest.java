package com.android.mail.utils;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * These test cases verify the handling of more advanced cross-site scripting attacks.
 */
@SmallTest
public class AdvancedHtmlSanitizerTest extends AndroidTestCase {
    public void testSampleEmail() {
        sanitize("<html>\n" +
                        "<head>\n" +
                        "<title>HTML E-mail</title>\n" +
                        "<script>\n" +
                        "alert(\"I am an alert box!\");\n" +
                        "</script>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "Body here\n" +
                        "<br />\n" +
                        "<a href=\"http://www.google.com\">Link to Google Search!</a>\n" +
                        "<br />\n" +
                        "<br />\n" +
                        "<a onclick=\"alert('surprise!')\" href=\"#\">I am a link!</a>\n" +
                        "<br />\n" +
                        "Moar body here\n" +
                        "</body>\n" +
                        "</html>"
                ,
                "\n" +
                        "\n" +
                        "\n" +
                        "\n" +
                        "\n" +
                        "<div>\n" +
                        "Body here\n" +
                        "<br />\n" +
                        "<a href=\"http://www.google.com\">Link to Google Search!</a>\n" +
                        "<br />\n" +
                        "<br />\n" +
                        "<a href=\"#\">I am a link!</a>\n" +
                        "<br />\n" +
                        "Moar body here\n" +
                        "</div>\n");
    }

    public void testXSS() {
        sanitize("'';!--\"<XSS>=&{()}", "&#39;&#39;;!--&#34;&#61;&amp;{()}");
        sanitize("<img src=javascript:alert(String.fromCharCode(88,83,83))>", "");
        sanitize("\\\";alert('XSS');//", "\\&#34;;alert(&#39;XSS&#39;);//");
        sanitize("<br size=\"&{alert('XSS')}\">", "<br />");
        sanitize("<xss style=\"xss:expression(alert('XSS'))\">", "");
        sanitize("<xss style=\"behavior: url(xss.htc);\">", "");
        sanitize("¼script¾alert(¢XSS¢)¼/script¾", "¼script¾alert(¢XSS¢)¼/script¾");
        sanitize("<xml><i><b><img src=\"javas<!-- -->cript:alert('XSS')\"></b></i></xml>",
                "<i><b></b></i>");
        sanitize("<xml src=\"xsstest.xml\" id=I></xml>", "");
        sanitize("<!--[if gte IE 4]>\n" +
                " <SCRIPT>alert('XSS');</SCRIPT>\n" +
                " <![endif]-->", "");
        sanitize("<body>\n" +
                        "<?xml:namespace prefix=\"t\" ns=\"urn:schemas-microsoft-com:time\">\n" +
                        "<?import namespace=\"t\" implementation=\"#default#time2\">\n" +
                        "<t:set attributeName=\"innerHTML\" to=\"XSS<script defer>alert(\"XSS\")" +
                        "</script>\">\n" +
                        "</body></html>",
                "<div>\n" +
                        "\n" +
                        "\n" +
                        "&#34;&gt;\n" +
                        "</div>");
    }

    /**
     * Technically, RFC 2392 doesn't limit where CID urls may appear. But, Webview is unhappy
     * handling them within link tags, so we only allow them in img src attributes until we see a
     * reason to expand their acceptance.
     */
    public void testCIDurls() {
        sanitize("<img src=\"http://www.here.com/awesome.png\"/>",
                "<img src=\"http://www.here.com/awesome.png\" />");
        sanitize("<img src=\"https://www.here.com/awesome.png\"/>",
                "<img src=\"https://www.here.com/awesome.png\" />");
        sanitize("<img src=\"cid:ii_145bda161daf6f9c\"/>",
                "<img src=\"cid:ii_145bda161daf6f9c\" />");

        sanitize("<a href=\"http://www.here.com/awesome.png\"/>",
                "<a href=\"http://www.here.com/awesome.png\"></a>");
        sanitize("<a href=\"https://www.here.com/awesome.png\"/>",
                "<a href=\"https://www.here.com/awesome.png\"></a>");
        sanitize("<a href=\"cid:ii_145bda161daf6f9c\"/>", "");
    }

    // todo the stock CssSchema in OWASP does NOT allow the float property; I experiment with adding
    // todo it to see how much it beautifies HTML display (the risk seems to be that you can display
    // todo content outside the bounds of your div and mislead the user with this technique)
    public void testCSS_float() {
        sanitize("<div style=\"float:none\"></div>", "<div style=\"float:none\"></div>");
        sanitize("<div style=\"float:left\"></div>", "<div style=\"float:left\"></div>");
        sanitize("<div style=\"float:right\"></div>", "<div style=\"float:right\"></div>");
        sanitize("<div style=\"float:inherit\"></div>", "<div style=\"float:inherit\"></div>");
        sanitize("<div style=\"float:initial\"></div>", "<div></div>");
        sanitize("<div style=\"float:garbage\"></div>", "<div></div>");
    }

    // todo the stock CssSchema in OWASP does NOT allow the display property; I experiment with
    // todo adding it to see how much it beautifies HTML display (the risk seems to be that you can
    // todo display content outside the bounds of your div and mislead the user with this technique)
    public void testCSS_display() {
        sanitize("<div style=\"display:inline\"></div>", "<div style=\"display:inline\"></div>");
        sanitize("<div style=\"display:block\"></div>", "<div style=\"display:block\"></div>");
        sanitize("<div style=\"display:flex\"></div>", "<div></div>");
        sanitize("<div style=\"display:inline-block\"></div>",
                "<div style=\"display:inline-block\"></div>");
        sanitize("<div style=\"display:inline-flex\"></div>", "<div></div>");
        sanitize("<div style=\"display:inline-table\"></div>",
                "<div style=\"display:inline-table\"></div>");
        sanitize("<div style=\"display:list-item\"></div>",
                "<div style=\"display:list-item\"></div>");
        sanitize("<div style=\"display:run-in\"></div>", "<div style=\"display:run-in\"></div>");
        sanitize("<div style=\"display:table\"></div>", "<div style=\"display:table\"></div>");
        sanitize("<div style=\"display:table-caption\"></div>",
                "<div style=\"display:table-caption\"></div>");
        sanitize("<div style=\"display:table-column-group\"></div>",
                "<div style=\"display:table-column-group\"></div>");
        sanitize("<div style=\"display:table-header-group\"></div>",
                "<div style=\"display:table-header-group\"></div>");
        sanitize("<div style=\"display:table-footer-group\"></div>",
                "<div style=\"display:table-footer-group\"></div>");
        sanitize("<div style=\"display:table-row-group\"></div>",
                "<div style=\"display:table-row-group\"></div>");
        sanitize("<div style=\"display:table-cell\"></div>",
                "<div style=\"display:table-cell\"></div>");
        sanitize("<div style=\"display:table-column\"></div>",
                "<div style=\"display:table-column\"></div>");
        sanitize("<div style=\"display:table-row\"></div>",
                "<div style=\"display:table-row\"></div>");
        sanitize("<div style=\"display:none\"></div>", "<div style=\"display:none\"></div>");
        sanitize("<div style=\"display:initial\"></div>", "<div></div>");
        sanitize("<div style=\"display:inherit\"></div>", "<div style=\"display:inherit\"></div>");
    }

    public void testTrimmingUrls() {
        // todo Gmail strips the leading space on this href
//        sanitize("<a href=\"http://www.google.com \">Send mail</a>",
//                "<a href=\"http://www.google.com\">Send mail</a>");
        sanitize("<a href=\" http://www.google.com\">Send mail</a>", "Send mail");
        // todo Gmail strips the trailing space on this href
//        sanitize("<a href=\"http://www.google.com \">Send mail</a> ",
//                "<a href=\"http://www.google.com\">Send mail</a>");
        sanitize("<a href=\"http://www.google.com \">Send mail</a>",
                "<a href=\"http://www.google.com \">Send mail</a>");
        // todo Gmail strips the leading and trailing spaces on this href
//        sanitize("<a href=\" http://www.google.com \">Send mail</a> ",
//                "<a href=\"http://www.google.com\">Send mail</a>");
        sanitize("<a href=\" http://www.google.com \">Send mail</a>", "Send mail");
    }

    public void testDangerousHtml() {
        // body tag is translated to div tag
        sanitize("<body dir=\"rtl\" onMouseOVer=\"alert(document.cookie)\">arr</body>",
                "<div dir=\"rtl\">arr</div>");
        sanitize("<DIV ONCLICK=alert(document.cookie) style=color:red>arr</DIV>",
                "<div style=\"color:red\">arr</div>");
        sanitize("<b style=position:absolute;left:0;top:0>arr</b>", "<b>arr</b>");

        // mailto: URLs on images are too easy to turn into DOS attacks
        sanitize("<img src=\"mailto:\">", "");
        sanitize("<img src=\"mailto:hcnidumolu@google.com\">", "");
        sanitize("<img src=\"mailto:hcnidumolu@google.com\">", "");
        sanitize("<img src=\" mailto:hcnidumolu@google.com\">", "");
        sanitize("<img src=\"m&#x61;ilto:hcnidumolu@google.com\">", "");
        sanitize("<img src=\"m&#x0D;ailto:hcnidumolu@google.com\">", "");
        // todo Gmail doesn't escape the @ sign; OWASP does by default
//        sanitize("<a href=\"mailto:hcnidumolu@google.com\">Send mail </a>",
//                "<a href=\"mailto:hcnidumolu@google.com\">Send mail </a>");
        sanitize("<a href=\"mailto:hcnidumolu@google.com\">Send mail </a>",
                "<a href=\"mailto:hcnidumolu&#64;google.com\">Send mail </a>");
    }

    public void testSanitizingImgsWithoutSchemes() {
        sanitize("<img src=\"//images1-gm-opensocial.googleusercontent.com/gadgets/proxy?" +
                        "url=http://foo.bar/baz.png&container=gm&gadget=a&rewriteMime=image/*\">",
//                "<img src=\"//images1-gm-opensocial.googleusercontent.com/gadgets/proxy?" +
//                        "url=http://foo.bar/baz.png&amp;container=gm&amp;gadget=" +
//                        "a&amp;rewriteMime=image/*\">"); // todo Gmail doesn't escape the = signs
                "<img src=\"//images1-gm-opensocial.googleusercontent.com/gadgets/proxy?" +
                        "url&#61;http://foo.bar/baz.png&amp;container&#61;gm&amp;gadget&#61;" +
                        "a&amp;rewriteMime&#61;image/*\" />");
    }

    public void testAdditionalURISchemes() {
        // todo Gmail keeps a destinationless link; OWASP strips the a link completely
//        sanitize("<a href=\"foo:bar\" target=\"_blank\">link1</a>", "<a>link1</a>");
        sanitize("<a href=\"foo:bar\" target=\"_blank\">link1</a>", "link1");
        // todo Gmail keeps a destinationless a link; OWASP strips the a link completely
//        sanitize("<a href=\"baz:alanbs@google.com\">link2</a>", "<a>link2</a>");
        sanitize("<a href=\"baz:alanbs@google.com\">link2</a>", "link2");
    }

    public void testBackgroundAttribute() {
        sanitize("<div background=\"http://www.random.org/png\">stuff</div><div>more stuff</div>",
                "<div background=\"http://www.random.org/png\">stuff</div><div>more stuff</div>");
    }

    public void testInputImage() {
        sanitize("<input type=\"image\" src=\"http://random.org/png\">",
                "<input type=\"image\" src=\"http://random.org/png\" />");
    }

    public void testImplicitInputImage() {
        // In HTML 4.01, src attribute has meaning only when type="image" (which
        // is not the default), but this happens in real life.
        sanitize("<input src=\"http://random.org/png\">",
                "<input src=\"http://random.org/png\" />");
    }

    public void testSerialization() {
        // N.B. (literal) newlines must not occur in CSS strings.
        // todo Gmail leaves this CSS style in place and escapes it; OWASP removes it all
//        sanitize("<a href=\"http://www.google.com\" style=\"font-family: 'expression; " +
//                        "\\a color:red;\\a font-family: completely unsanitized =(;' ;\">asdf</a>",
//                "<a href=\"http://www.google.com\" style=\"font-family:&#39;expression; " +
//                        "\\00000acolor:red;\\00000afont-family: completely unsanitized " +
//                        "=(;&#39;\">asdf</a>");
        sanitize("<a href=\"http://www.google.com\" style=\"font-family: 'expression; " +
                        "\\a color:red;\\a font-family: completely unsanitized =(;' ;\">asdf</a>",
                "<a href=\"http://www.google.com\">asdf</a>");
    }

    public void testNoJS() {
        // todo Gmail leaves this CSS in place and escapes it; OWASP removes it all
//        sanitize("<a href=\"http://www.google.com\" style=\"background-image: " +
//                "url('javascript:alert(1)')\"></a>",
//                "<a href=\"http://www.google.com\" style=\"background-image:" +
//                        "url(&#39;&#39;)\"></a>");
        sanitize("<a href=\"http://www.google.com\" style=\"background-image: " +
                "url('javascript:alert(1)')\"></a>",
                "<a href=\"http://www.google.com\"></a>");
    }

    public void testNoStyleElementByDefault() {
        sanitize("<head><style type='text/css'>verboten { color: red; }</style></head>" +
                        "<body><p>test</p>",
                "<div><p>test</p></div>");
    }

    public void testMessageFormation() {
        sanitize("<table><tr><td><b>This is a simple message</b></td></tr></table>",
                "<table><tr><td><b>This is a simple message</b></td></tr></table>");
        sanitize("<table><tr><td><b>This is a simple message",
                "<table><tr><td><b>This is a simple message</b></td></tr></table>");
        sanitize("<table><tr>This is a simple message</b></td></tr></table>",
                "<table><tr><td>This is a simple message</td></tr></table>");
        sanitize("This is a simple message</b></td></tr></table>", "This is a simple message");
    }

    public void testViolatingTags() {
        sanitize("<html><head><title>html to ruin your site</title>"
                        + "<meta http-equiv=\"refresh\" content=\"5\" />"
                        + "<link rel=\"stylesheet\" type=\"text/css\"  href=\"some site\"/>"
                        + "<style type=\"text/css\"> h1 {color: red}</style>"
                        + "</head><body><script>some script to run</script>"
                        + "<noscript>Please enable Javascript and reload this page."
                        + "Good things abound!</noscript>"
                        + "<noframes>This page requires frames!</noframes>"
                        + "<frameset cols = \"25%, 25%,*\">"
                        + "<frame src=\"site1.htm\" />"
                        + "<frame src=\"site2.htm\" />"
                        + "<frame src=\"site3.htm\" /> "
                        + "</frameset>"
                        + "<table><tr><td>"
                        + "Execute this <applet code=\"some evil site\"></td>"
                        + "</tr></table></body></html>"
                ,
                "<div>"
                        + "<table><tr><td>"
                        + "Execute this </td>"
                        + "</tr></table></div>"
        );

        sanitize("Include this:<br/>"
                        + "<object classid=\"clsid:FOOBAR\" id=\"Slider1\""
                        + "declare=\"declare\">"
                        + "<param name=\"some param\" value=\"1\" />"
                        + "</object><br/>"
                        + "<form method=\"POST\" action=\"http://www.somesite.com\""
                        + "onsubmit=\"run some script\">"
                        + "<input type=\"text\" onclick=\"run some script\" id=\"input\"/>"
                        + "<input type=\"submit\" onfocus=\"run some script\" value=\"submit\"/>"
                        + "</form>"
                ,
                "Include this:<br />"
                        + "<br />"
                        + "<form method=\"POST\" action=\"http://www.somesite.com\">"
                        + "<input type=\"text\" />"
                        + "<input type=\"submit\" value=\"submit\" />"
                        + "</form>"
        );
    }

    public void testLinks() {
        sanitize("<a href=\"http://www.somesite.com\" target=\"_self\" "
                        + "onclick=\"run some script\" onmouseover=\"run some script\">"
                        + "click here</a>"
                        + "<a href=\"javascript:run some script\">here</a>"
                        + "<a href=\"someinternalpage.htm\">or here</a>"
                ,
                "<a href=\"http://www.somesite.com\">"
                        + "click here</a>"
                        + "here"
                        + "<a href=\"someinternalpage.htm\">or here</a>"
        );
    }

    public void testExternalLinks() {
        sanitize("This is a test <a href=http://google.com>here</a> "
                        + "<img src=\"http://google.com/bogus.jpg\">"
                        + "<img src=\"//google.com/bogus2.jpg\">"
                        + "<img src=\"google.com/bogus3.jpg\">"
                        + "<script>Hello</script> "
                        + "<frameset><frame src=foo name=onlyFrame>hey</frame></frameset>"
                ,
                "This is a test <a href=\"http://google.com\">here</a> "
                        + "<img src=\"http://google.com/bogus.jpg\" />"
                        + "<img src=\"//google.com/bogus2.jpg\" />"
                        + "<img src=\"google.com/bogus3.jpg\" /> "
        );
    }

    public void testNewHtmlWhitelist() {
        sanitize("<a href=http://google.com/boguslink>link</a>"
                        + "<b>BOLD</b>"
                        + "<i>italics</i>"
                        + "<u>underlined</u>"
                        + "<br/>break<br>break"
                        + "<font size=+1>Big_font_gone</font>"
                ,
                "<a href=\"http://google.com/boguslink\">link</a>"
                        + "<b>BOLD</b>"
                        + "<i>italics</i>"
                        + "<u>underlined</u>"
                        + "<br />break<br />break"
                        + "<font size=\"&#43;1\">Big_font_gone</font>"
        );
    }

    public void testRemoveBackticksInAttributes() {
        // IE treats backticks as quotes when re-serializing, but not when parsing
        sanitize("<img alt=\"``onload=alert(1)\">",
                "<img alt=\"&#96;&#96;onload&#61;alert(1) \" />");
        sanitize("<img alt=\"'``onload=alert(1)'\">",
                "<img alt=\"&#39;&#96;&#96;onload&#61;alert(1)&#39; \" />");
        sanitize("<img alt=``onload=alert(1)\">", "<img alt=\"&#96;&#96;onload&#61;alert(1) \" />");

        // Make sure we're not fooled by escaped backticks
        sanitize("<img alt=\"&#96;&#x0060;onload=alert(1)\">",
                "<img alt=\"&#96;&#96;onload&#61;alert(1) \" />");
        sanitize("<img alt=\"&#x000060;&#x000060;onload=alert(1)\">",
                "<img alt=\"&#96;&#96;onload&#61;alert(1) \" />");

        // Misc. dangerous cases:
        sanitize("<img alt=`x`onload=alert(1)>", "<img alt=\"&#96;x&#96;onload&#61;alert(1) \" />");
        sanitize("<img alt=foo`x`onload=alert(1)>",
                "<img alt=\"foo&#96;x&#96;onload&#61;alert(1) \" />");
        sanitize("<img alt=\"`whatever\">Hello world ` onload=alert(1) <br>",
                "<img alt=\"&#96;whatever \" />Hello world &#96; onload&#61;alert(1) <br />");

        // The tokenizer doesn't see these as entities because they lack a trailing semicolon, so it
        // escapes the leading ampersands.
        sanitize("<img alt=\"&#x000060&#x000060onload=alert(1)\">",
                "<img alt=\"&#96;&amp;#x000060onload&#61;alert(1) \" />");

        // Here there are no actual backticks, though there would be if we (or IE) did repeated
        // unescaping.
        sanitize("<img alt=\"&amp;#x000060&amp;#x000060onload=alert(2)\">",
                "<img alt=\"&amp;#x000060&amp;#x000060onload&#61;alert(2)\" />");
        sanitize("<img alt=\"&amp;amp;#x000060&amp;amp;#x000060onload=alert(2)\">",
                "<img alt=\"&amp;amp;#x000060&amp;amp;#x000060onload&#61;alert(2)\" />");
    }

    public void testMakeSafeStyle() {
        sanitize("<div style=\"color:red\"></div>", "<div style=\"color:red\"></div>");
        sanitize("<div style=\"color:r\\ne\\t d  d\\r\\n\"></div>", "<div></div>");
        sanitize("<div style=\"font-size:13.5pt; color:#804000 \"></div>",
                "<div style=\"font-size:13.5pt;color:#804000\"></div>");
        sanitize("<div style=\"color:red;color\"></div>", "<div style=\"color:red\"></div>");
        sanitize("<div style=\"color:red;color:a:b\"></div>", "<div style=\"color:red\"></div>");
        sanitize("<div style=\"color:url(foo)\"></div>", "<div></div>");
        sanitize("<div style=\"color:white; list-style:url(foo.gif);\"></div>",
                "<div style=\"color:white\"></div>");
        sanitize("<div style=\"color:rgb(255, 0, 0)\"></div>",
                "<div style=\"color:rgb( 255 , 0 , 0 )\"></div>");
        sanitize("<div style=\"background-color:rgb(80%,92%,18%)\"></div>",
                "<div style=\"background-color:rgb( 80% , 92% , 18% )\"></div>");
        sanitize("<div style=\"border-left:1px rgb(0,255,0) solid\"></div>",
                "<div style=\"border-left:1px rgb( 0 , 255 , 0 ) solid\"></div>");
        sanitize("<div style=\"background:rgb(0,255,0) url(foo) no-repeat top\"></div>",
                "<div style=\"background:rgb( 0 , 255 , 0 ) no-repeat top\"></div>");
        sanitize("<div style=\"display:none; border-color: #ffeeff \"></div>",
                "<div style=\"display:none;border-color:#ffeeff\"></div>");

        // check for CSS3 border-radius
        sanitize("<div style=\"border-radius:10px\"></div>",
                "<div style=\"border-radius:10px\"></div>");
        sanitize("<div style=\"border-bottom-left-radius:10px\"></div>",
                "<div style=\"border-bottom-left-radius:10px\"></div>");
        sanitize("<div style=\"border-bottom-right-radius:10px\"></div>",
                "<div style=\"border-bottom-right-radius:10px\"></div>");
        sanitize("<div style=\"border-top-left-radius:10px\"></div>",
                "<div style=\"border-top-left-radius:10px\"></div>");
        sanitize("<div style=\"border-top-right-radius:10px\"></div>",
                "<div style=\"border-top-right-radius:10px\"></div>");

        // allow positive margins
        sanitize("<div style=\"margin:10 0 10 0\"></div>",
                "<div style=\"margin:10 0 10 0\"></div>");
        sanitize("<div style=\"margin-left:40px\"></div>",
                "<div style=\"margin-left:40px\"></div>");

        // negative margin would allow it to slip out of the box
        sanitize("<div style=\"margin-left:-10\"></div>", "<div></div>");

        // allow positive text-ident
        sanitize("<div style=\"text-indent:10\"></div>", "<div style=\"text-indent:10\"></div>");
        sanitize("<div style=\"text-indent:0\"></div>", "<div style=\"text-indent:0\"></div>");

        // todo Gmail disallows negative text-indents; OWASP is fine with them
        // negative text-indent would allow it to slip out of the box
//        sanitize("<div style=\"text-indent:-10\"></div>", "<div></div>");
        sanitize("<div style=\"text-indent:-10\"></div>", "<div style=\"text-indent:-10\"></div>");
    }

    public void testMakeSafeStyleWithQuotedStrings() {
        sanitize("<div style=\"font-family:'courier new',monospace;font-size:x-small\"></div>",
                "<div style=\"font-family:&#39;courier new&#39; , monospace;font-size:x-small\">" +
                        "</div>");
        sanitize("<div style=\"font-family:\"courier new\",monospace\"></div>", "<div></div>");
        sanitize("<div style=\"font-family:''\"></div>", "<div></div>");
        sanitize("<div style=\"font-family:a,''\"></div>",
                "<div style=\"font-family:&#39;a&#39; ,\"></div>");
        sanitize("<div style=\"font-family:'',a,\"\",b\"></div>",
                "<div style=\"font-family:, &#39;a&#39; ,\"></div>");

        sanitize("<div style=\"font-family:'\"></div>", "<div></div>");
        sanitize("<div style=\"font-family: 'courier new\",monospace;'\"></div>", "<div></div>");
        sanitize("<div style=\"font-family: \"courier new',monospace;\"></div>", "<div></div>");
    }

    public void testSeriouslyNoBackgroundImages() {
        sanitize("<div style=\"background:url('http://www.here.com/awesome.png')\"></div>",
                "<div></div>");
        sanitize("<div style=\"background-image:url('http://www.here.com/awesome.png')\"></div>",
                "<div></div>");

        sanitize("<div style=\"background:url('javascript:evil()')\"></div>", "<div></div>");
        sanitize("<div style=\"background-image:url('javascript:evil()')\"></div>", "<div></div>");
    }

    public void testExpression() {
        sanitize("<div style=\"width: expression(alert(1))\"></div>", "<div></div>");
    }

    public void testStrayUrlConsideredHarmful() {
        sanitize("<div style=\"float:url(\"></div>", "<div></div>");
        sanitize("<div style=\"float:\\075\\0072\\006C\\0028\"></div>", "<div></div>");
    }

    public void testObjectionableFunctions() {
        sanitize("<div style=\"ex\\pression(123)\"></div>", "<div></div>");
        sanitize("<div style=\"_expression(123)\"></div>", "<div></div>");
        sanitize("<div style=\"ｅｘｐｒｅｓｓｉｏｎ(123)\"></div>", "<div></div>");
        sanitize("<div style=\"funkyFunction(123)\"></div>", "<div></div>");

        sanitize("<div style=\"color:expression(alert('xss'))\"></div>", "<div></div>");
        sanitize("<div style=\"color:expression(alert\\000028\\000027xss\\000027\\000029)\"></div>",
                "<div></div>");
        sanitize("<div style=\"color:expression\\000028alert\\000028\\000027xss\\000027" +
                "\\000029\\000029\"></div>", "<div></div>");
        sanitize("<div style=\"color:expressio\\00006E\\000028alert\\000028\\000027xss\\000027" +
                "\\000029\\000029\"\"></div>", "<div></div>");
        sanitize("<div style=\"color:expression\\(alert\\)\"></div>", "<div></div>");
    }

    public void testAbsolutePositionBanned() {
        sanitize("<div style=\"position: absolute\"></div>", "<div></div>");
    }

    public void testNoTextShadow() {
        // todo Gmail disallows this text-shadow; OWASP is fine with it
//        sanitize("<div style=\"text-shadow: red -50px -100px 0px\"></div>", "<div></div>");
        sanitize("<div style=\"text-shadow: red -50px -100px 0px\"></div>",
                "<div style=\"text-shadow:red -50px -100px 0px\"></div>");
    }

    public void testToStyle() {
        sanitize("<div style=\"color:red\"></div>", "<div style=\"color:red\"></div>");
        sanitize("<div style=\"color: red\"></div>", "<div style=\"color:red\"></div>");
        sanitize("<div style=\"color :red\"></div>", "<div style=\"color:red\"></div>");
        sanitize("<div style=\"color :red; font-size:13.5pt;\"></div>",
                "<div style=\"color:red;font-size:13.5pt\"></div>");
        sanitize("<div style=\"content:'\"'\"></div>", "<div></div>");
    }

    public void testNoColorStrings() {
        sanitize("<div style=\"color: 'red';\"></div>", "<div></div>");
    }

    public void testTolerateMalformedBorder() {
        sanitize("<div style=\"border-left-: solid thin red\"></div>", "<div></div>");
    }

    public void testRgba() {
        sanitize("<div style=\"color:rgba(255, 0, 0, 0.5)\"></div>",
                "<div style=\"color:rgba( 255 , 0 , 0 , 0.5 )\"></div>");
    }

    public void testFontStyle() {
        // todo Gmail accepts !important while OWASP discards it; this is only beauty, not security
//        sanitize("<div style=\"font-style:normal!important\"></div>",
//                "<div style=\"font-style:normal!important\"></div>");
        sanitize("<div style=\"font-style:normal!important\"></div>",
                "<div style=\"font-style:normal\"></div>");
        // todo Gmail accepts !important while OWASP discards it; this is only beauty, not security
//        sanitize("<div style=\"font-style:oblique !important\"></div>",
//                "<div style=\"font-style:oblique!important\"></div>");
        sanitize("<div style=\"font-style:oblique !important\"></div>",
                "<div style=\"font-style:oblique\"></div>");
        sanitize("<div style=\"font-style:italic\"></div>",
                "<div style=\"font-style:italic\"></div>");
    }

    private void sanitize(String dirtyHTML, String expectedHTML) {
        final String cleansedHTML = HtmlSanitizer.sanitizeHtml(dirtyHTML);
        assertEquals(expectedHTML, cleansedHTML);
    }
}
