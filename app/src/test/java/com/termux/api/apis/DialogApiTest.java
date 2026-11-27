package com.termux.api.apis;

import android.app.AlertDialog;
import android.app.Application;
import android.content.Intent;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34)
public class DialogApiTest {

    @Test
    public void sheetHonorsTitleAndListResults() {
        AppCompatActivity activity = Robolectric.setupActivity(AppCompatActivity.class);
        activity.setIntent(new Intent()
            .putExtra("input_title", "Choose a terminal")
            .putExtra("input_values", "first, second\\, option"));
        DialogAPI.DialogActivity.InputMethod method =
            DialogAPI.DialogActivity.InputMethodFactory.get("sheet", activity);
        List<DialogAPI.DialogActivity.InputResult> results = new ArrayList<>();

        method.create(activity, results::add);
        AlertDialog dialog = (AlertDialog) method.getDialog();
        assertEquals("Choose a terminal", shadowOf(dialog).getTitle().toString());
        shadowOf(dialog).clickOnItem(1);
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).index);
        assertEquals("second, option", results.get(0).text);

        method.create(activity, results::add);
        method.getDialog().cancel();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(2, results.size());
        assertEquals(AlertDialog.BUTTON_NEGATIVE, results.get(1).code);
        assertEquals(-1, results.get(1).index);
    }
}
