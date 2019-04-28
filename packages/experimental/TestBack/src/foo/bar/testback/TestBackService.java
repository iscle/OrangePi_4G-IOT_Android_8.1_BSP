package foo.bar.testback;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.util.ArraySet;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;

import java.util.List;
import java.util.Set;

public class TestBackService extends AccessibilityService {

    private static final String LOG_TAG = TestBackService.class.getSimpleName();

    private Button mButton;

    @Override
    public void onCreate() {
        super.onCreate();
        mButton = new Button(this);
        mButton.setText("Button 1");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
//        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
//            Log.i(LOG_TAG, event.getText().toString());
//            //dumpWindows();
//        }
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER) {
//            Log.i(LOG_TAG, "Click event.isChecked()=" + event.isChecked()
//                    + ((event.getSource() != null) ? " node.isChecked()="
//                    + event.getSource().isChecked() : " node=null"));

            AccessibilityNodeInfo source = event.getSource();
            dumpWindow(source);
//            AccessibilityNodeInfo node = event.getSource();
//            if (node != null) {
//                node.refresh();
//                Log.i(LOG_TAG, "Clicked: " + node.getClassName() + " clicked:" + node.isChecked());
//            }
        }
    }

    @Override
    protected boolean onGesture(int gestureId) {
        switch (gestureId) {
            case AccessibilityService.GESTURE_SWIPE_DOWN: {
                showAccessibilityOverlay();
            } break;
            case AccessibilityService.GESTURE_SWIPE_UP: {
                hideAccessibilityOverlay();
            } break;
        }
        return super.onGesture(gestureId);
    }

    private void showAccessibilityOverlay() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;

        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        windowManager.addView(mButton, params);
    }

    private void hideAccessibilityOverlay() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        windowManager.removeView(mButton);
    }

    private void dumpWindows() {
        List<AccessibilityWindowInfo> windows = getWindows();
        final int windowCount = windows.size();
        for (int i = 0; i < windowCount; i++) {
            AccessibilityWindowInfo window = windows.get(i);
            Log.i(LOG_TAG, "=============================");
            Log.i(LOG_TAG, window.toString());

        }
    }

    private void dumpWindow(AccessibilityNodeInfo source) {
        AccessibilityNodeInfo root = source;
        while (true) {
            AccessibilityNodeInfo parent = root.getParent();
            if (parent == null) {
                break;
            } else if (parent.equals(root)) {
                Log.i(LOG_TAG, "Node is own parent:" + root);
            }
            root = parent;
        }
        dumpTree(root, new ArraySet<AccessibilityNodeInfo>());
    }

    private void dumpTree(AccessibilityNodeInfo root, Set<AccessibilityNodeInfo> visited) {
        if (root == null) {
            return;
        }

        if (!visited.add(root)) {
            Log.i(LOG_TAG, "Cycle detected to node:" + root);
        }

        final int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo parent = child.getParent();
                if (parent == null) {
                    Log.e(LOG_TAG, "Child of a node has no parent");
                } else if (!parent.equals(root)) {
                    Log.e(LOG_TAG, "Child of a node has wrong parent");
                }
                Log.i(LOG_TAG, child.toString());
            }
        }

        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            dumpTree(child, visited);
        }
    }

    @Override
    public void onInterrupt() {
        /* ignore */
    }

    @Override
    public void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }
}
