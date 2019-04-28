package foo.bar.fill;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.ViewNode;
import android.content.Intent;
import android.os.Bundle;
import android.service.autofill.Dataset;
import android.service.autofill.FillResponse;
import android.view.View;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.widget.Button;
import android.widget.RemoteViews;

public class AuthActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        AssistStructure structure = getIntent().getParcelableExtra(
                AutofillManager.EXTRA_ASSIST_STRUCTURE);

        ViewNode username = FillService.findUsername(structure);
        ViewNode password = FillService.findPassword(structure);

        final FillResponse response;
        final Dataset dataset;

        if (FillService.TEST_RESPONSE_AUTH) {
            RemoteViews presentation1 = new RemoteViews(getPackageName(), R.layout.list_item);
            presentation1.setTextViewText(R.id.text1,FillService.DATASET1_NAME);

            RemoteViews presentation2 = new RemoteViews(getPackageName(), R.layout.list_item);
            presentation2.setTextViewText(R.id.text1,FillService.DATASET2_NAME);

            response = new FillResponse.Builder()
                    .addDataset(new Dataset.Builder(presentation1)
                            .setValue(username.getAutofillId(),
                                    AutofillValue.forText(FillService.DATASET1_USERNAME))
                            .setValue(password.getAutofillId(),
                                    AutofillValue.forText(FillService.DATASET1_PASSWORD))
                            .build())
                    .addDataset(new Dataset.Builder(presentation2)
                            .setValue(username.getAutofillId(),
                                    AutofillValue.forText(FillService.DATASET2_USERNAME))
                            .setValue(password.getAutofillId(),
                                    AutofillValue.forText(FillService.DATASET2_PASSWORD))
                            .build())
                    .build();
            dataset = null;
        } else {
            RemoteViews presentation = new RemoteViews(getPackageName(), R.layout.list_item);
            presentation.setTextViewText(R.id.text1,FillService.DATASET2_NAME);

            dataset = new Dataset.Builder(presentation)
                    .setValue(username.getAutofillId(),
                            AutofillValue.forText(FillService.DATASET5_USERNAME))
                    .setValue(password.getAutofillId(),
                            AutofillValue.forText(FillService.DATASET5_PASSWORD))
                    .build();
            response = null;
        }

        Button button = (Button) findViewById(R.id.confirm);
        button.setOnClickListener((View v) -> {
            Intent result = new Intent();
            if (FillService.TEST_RESPONSE_AUTH) {
                result.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response);
            } else {
                result.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset);
            }
            setResult(RESULT_OK, result);
            finish();
        });
    }
}
