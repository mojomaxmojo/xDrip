package com.eveningoutpost.dexdrip.insulin;

import android.os.Bundle;
import android.widget.EditText;

import com.eveningoutpost.dexdrip.BaseAppCompatActivity;
import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.models.JoH;

/**
 * Form to create or edit a user-defined custom insulin profile.
 * Profiles are stored in preferences and merged with the predefined
 * profiles from insulin_profiles.json by InsulinManager.
 */
public class InsulinProfileAddActivity extends BaseAppCompatActivity {

    private static final String TAG = "InsulinProfileAdd";
    public static final String EXTRA_PROFILE_NAME = "profile_name";

    private EditText nameField;
    private EditText displayNameField;
    private EditText concentrationField;
    private EditText ppnField;
    private EditText onsetField;
    private EditText peakField;
    private EditText durationField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_insulin_profile_add);

        nameField = (EditText) findViewById(R.id.insulinNameField);
        displayNameField = (EditText) findViewById(R.id.insulinDisplayNameField);
        concentrationField = (EditText) findViewById(R.id.insulinConcentrationField);
        ppnField = (EditText) findViewById(R.id.insulinPpnField);
        onsetField = (EditText) findViewById(R.id.insulinOnsetField);
        peakField = (EditText) findViewById(R.id.insulinPeakField);
        durationField = (EditText) findViewById(R.id.insulinDurationField);

        concentrationField.setText("U100");
        onsetField.setText("15");
        peakField.setText("90");
        durationField.setText("300");

        // if a profile name was supplied, pre-fill the form for editing that profile
        final String editName = getIntent() != null ? getIntent().getStringExtra(EXTRA_PROFILE_NAME) : null;
        if (editName != null && !editName.isEmpty()) {
            loadProfileForEditing(editName);
        }
    }

    private void loadProfileForEditing(String name) {
        try {
            final com.google.gson.JsonArray array = InsulinManager.getCustomProfilesJsonPublic();
            for (com.google.gson.JsonElement element : array) {
                final com.google.gson.JsonObject obj = element.getAsJsonObject();
                if (obj.has("name") && name.equalsIgnoreCase(obj.get("name").getAsString())) {
                    nameField.setText(obj.get("name").getAsString());
                    if (obj.has("displayName")) displayNameField.setText(obj.get("displayName").getAsString());
                    if (obj.has("concentration")) concentrationField.setText(obj.get("concentration").getAsString());
                    if (obj.has("PPN")) {
                        final StringBuilder sb = new StringBuilder();
                        for (com.google.gson.JsonElement ppn : obj.getAsJsonArray("PPN")) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(ppn.getAsString());
                        }
                        ppnField.setText(sb.toString());
                    }
                    if (obj.has("Curve") && obj.getAsJsonObject("Curve").has("data")) {
                        final com.google.gson.JsonObject data = obj.getAsJsonObject("Curve").getAsJsonObject("data");
                        if (data.has("onset")) onsetField.setText(data.get("onset").getAsString());
                        if (data.has("peak")) peakField.setText(data.get("peak").getAsString());
                        if (data.has("duration")) durationField.setText(data.get("duration").getAsString());
                    }
                    setTitle(R.string.title_insulin_profile_edit);
                    return;
                }
            }
        } catch (Exception e) {
            JoH.static_toast_long("Error loading profile: " + e);
        }
    }

    public void saveButtonClick(android.view.View view) {
        final String error = InsulinManager.saveCustomProfile(
                nameField.getText().toString(),
                displayNameField.getText().toString(),
                concentrationField.getText().toString(),
                ppnField.getText().toString(),
                onsetField.getText().toString(),
                peakField.getText().toString(),
                durationField.getText().toString());
        if (error != null) {
            JoH.static_toast_long(error);
            return;
        }
        JoH.static_toast_long(getString(R.string.insulin_profile_saved));
        InsulinManager.LoadDisabledProfilesFromPrefs();
        finish();
    }

    public void cancelButtonClick(android.view.View view) {
        finish();
    }
}
