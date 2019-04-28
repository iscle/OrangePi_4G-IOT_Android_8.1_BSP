package foo.bar.filled;

import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStructure;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CustomLinearLayout extends LinearLayout {
    static final boolean VIRTUAL = false;

    public CustomLinearLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        if (VIRTUAL) {
            getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
                AutofillManager autofillManager = getContext().getSystemService(
                        AutofillManager.class);
                if (oldFocus != null) {
                    autofillManager.notifyViewExited(CustomLinearLayout.this,
                            oldFocus.getAccessibilityViewId());
                }
                if (newFocus != null) {
                    Rect bounds = new Rect();
                    newFocus.getBoundsOnScreen(bounds);
                    autofillManager.notifyViewEntered(CustomLinearLayout.this,
                            newFocus.getAccessibilityViewId(), bounds);
                }
            });
        }
    }

    @Override
    public void dispatchProvideAutofillStructure(ViewStructure structure, int flags) {
        if (!VIRTUAL) {
            super.dispatchProvideAutofillStructure(structure, flags);
        } else {
            onProvideAutofillVirtualStructure(structure, flags);
        }
    }

    @Override
    public void onProvideAutofillVirtualStructure(ViewStructure structure, int flags) {
        if (!VIRTUAL) {
            return;
        }
        populateViewStructure(this, structure);
        onProvideAutofillVirtualStructureRecursive(this, structure);
    }

    @Override
    public void autofill(SparseArray<AutofillValue> values) {
        final int valueCount = values.size();
        for (int i = 0; i < valueCount; i++) {
            final int virtualId = values.keyAt(i);
            final AutofillValue value = values.valueAt(i);
            View view = findViewByAccessibilityIdTraversal(virtualId);
            if (view instanceof EditText && !TextUtils.isEmpty(value.getTextValue())) {
                EditText editText = (EditText) view;
                editText.setText(value.getTextValue());

            }
        }
    }

    private void onProvideAutofillVirtualStructureRecursive(View view, ViewStructure node) {
        if (node == null) {
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            final int childCount = viewGroup.getChildCount();
            node.setChildCount(childCount);
            for (int i = 0; i < childCount; i++) {
                View child = viewGroup.getChildAt(i);
                ViewStructure chlidNode = node.newChild(i);
                chlidNode.setAutofillId(node, child.getAccessibilityViewId());
                populateViewStructure(child, chlidNode);
                onProvideAutofillVirtualStructureRecursive(child, chlidNode);
            }
        }
    }

    private void populateViewStructure(View view, ViewStructure structure) {
        if (view.getId() != NO_ID) {
            String pkg, type, entry;
            try {
                final Resources res = getResources();
                entry = res.getResourceEntryName(view.getId());
                type = res.getResourceTypeName(view.getId());
                pkg = res.getResourcePackageName(view.getId());
            } catch (Resources.NotFoundException e) {
                entry = type = pkg = null;
            }
            structure.setId(view.getId(), pkg, type, entry);
        } else {
            structure.setId(view.getId(), null, null, null);
        }
        Rect rect = structure.getTempRect();
        view.getDrawingRect(rect);
        structure.setDimens(rect.left, rect.top, 0, 0, rect.width(), rect.height());
        structure.setVisibility(VISIBLE);
        structure.setEnabled(view.isEnabled());
        if (view.isClickable()) {
            structure.setClickable(true);
        }
        if (view.isFocusable()) {
            structure.setFocusable(true);
        }
        if (view.isFocused()) {
            structure.setFocused(true);
        }
        if (view.isAccessibilityFocused()) {
            structure.setAccessibilityFocused(true);
        }
        if (view.isSelected()) {
            structure.setSelected(true);
        }
        if (view.isLongClickable()) {
            structure.setLongClickable(true);
        }
        CharSequence cname = view.getClass().getName();
        structure.setClassName(cname != null ? cname.toString() : null);
        structure.setContentDescription(view.getContentDescription());
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            structure.setText(textView.getText(), textView.getSelectionStart(),
                    textView.getSelectionEnd());
        }
        structure.setAutofillHints(view.getAutofillHints());
        structure.setAutofillType(view.getAutofillType());
        structure.setAutofillValue(view.getAutofillValue());
    }
}
