package com.reiniertutoriales.vireotest;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Base64;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;
import org.xmlpull.v1.XmlSerializer;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/** Read-only test bridge. Never packaged in VireoExplorerTV. */
public final class Hierarchy extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        AccessibilityNodeInfo root = null;
        try {
            UiAutomation automation = getUiAutomation();
            for (int i = 0; i < 12 && root == null; i++) {
                root = automation.getRootInActiveWindow();
                if (root == null) android.os.SystemClock.sleep(100);
            }
            if (root == null) throw new IllegalStateException("No active accessibility root");
            StringWriter writer = new StringWriter();
            XmlSerializer xml = Xml.newSerializer();
            xml.setOutput(writer);
            xml.startDocument("UTF-8", true);
            xml.startTag(null, "hierarchy");
            append(xml, root);
            xml.endTag(null, "hierarchy");
            xml.endDocument();
            result.putString("hierarchy", Base64.encodeToString(
                writer.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
            finish(Activity.RESULT_OK, result);
        } catch (Exception error) {
            result.putString("error", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (root != null) root.recycle();
        }
    }

    private static void append(XmlSerializer xml, AccessibilityNodeInfo node) throws Exception {
        xml.startTag(null, "node");
        xml.attribute(null, "text", clean(node.getText()));
        xml.attribute(null, "class", clean(node.getClassName()));
        xml.attribute(null, "focused", Boolean.toString(node.isFocused()));
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        xml.attribute(null, "bounds", "[" + bounds.left + "," + bounds.top + "][" + bounds.right + "," + bounds.bottom + "]");
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try { append(xml, child); } finally { child.recycle(); }
        }
        xml.endTag(null, "node");
    }

    private static String clean(CharSequence text) {
        if (text == null) return "";
        StringBuilder safe = new StringBuilder();
        text.toString().codePoints().forEach(c -> {
            if (c == 9 || c == 10 || c == 13 || (c >= 32 && c <= 0xD7FF)
                || (c >= 0xE000 && c <= 0xFFFD) || (c >= 0x10000 && c <= 0x10FFFF))
                safe.appendCodePoint(c);
        });
        return safe.toString();
    }
}
