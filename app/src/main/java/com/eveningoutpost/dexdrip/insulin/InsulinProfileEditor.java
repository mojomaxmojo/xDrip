package com.eveningoutpost.dexdrip.insulin;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.eveningoutpost.dexdrip.BaseAppCompatActivity;
import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.models.JoH;

import java.util.ArrayList;
import java.util.HashMap;

import lombok.val;

/**
 * Created by gruoner on 28/07/2019.
 */

public class InsulinProfileEditor extends BaseAppCompatActivity {

    private static final String TAG = "insulinprofileeditor";

    // TODO are these buttons actually used?
    private Button cancelBtn;
    private Button saveBtn;
    private Button undoBtn;
    private LinearLayout linearLayout;
    private Spinner basalSpinner, bolusSpinner;
    private HashMap<Insulin, CheckBox> checkboxes;
    private HashMap<String, Insulin> profiles;
    private boolean resumedOnce = false;
    private boolean recreating = false;

    //private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //mContext = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_insulinprofile_editor);

        checkboxes = new HashMap<Insulin, CheckBox>();
        profiles = new HashMap<>();

        undoBtn = (Button) findViewById(R.id.profileUndoBtn);
        saveBtn = (Button) findViewById(R.id.profileSaveBtn);
        cancelBtn = (Button) findViewById(R.id.profileCancelbtn);
        linearLayout = (LinearLayout) findViewById(R.id.profile_layout_view);
        basalSpinner = (Spinner) findViewById(R.id.basalSpinner);
        bolusSpinner = (Spinner) findViewById(R.id.bolusSpinner);

        val iprofiles = InsulinManager.getAllProfiles();
        if (iprofiles == null) {
            JoH.static_toast_long("Can't initialize insulin profiles");
            finish();
            return;
        }
        for (Insulin i : iprofiles) {
            LinearLayout v = new LinearLayout(this);
            v.setOrientation(LinearLayout.HORIZONTAL);
            CheckBox cb = new CheckBox(this);
            if (InsulinManager.isProfileEnabled(i))
                cb.setChecked(true);
            else
                cb.setChecked(false);
            cb.setText(i.getDisplayName());
            cb.setTextSize(20);
            checkboxes.put(i, cb);
            cb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (InsulinManager.isProfileEnabled(i))
                        InsulinManager.disableProfile(i);
                    else
                        InsulinManager.enableProfile(i);
                    if (InsulinManager.isProfileEnabled(i))
                        cb.setChecked(true);
                    else
                        cb.setChecked(false);
                }
            });
            // long-press on a custom (user-defined) profile offers editing or deletion
            if (InsulinManager.isCustomProfile(i.getName())) {
                cb.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        showCustomProfileOptions(i);
                        return true;
                    }
                });
            }
            v.addView(cb);
            linearLayout.addView(v);
            profiles.put(i.getDisplayName(), i);
        }
        ArrayList<String> p = new ArrayList<String>(profiles.keySet());
        ArrayAdapter<String> profilesAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, p);
        profilesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        basalSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                InsulinManager.setBasalProfile(profiles.get(adapterView.getItemAtPosition(i)));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        basalSpinner.setAdapter(profilesAdapter);
        bolusSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                InsulinManager.setBolusProfile(profiles.get(adapterView.getItemAtPosition(i)));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        bolusSpinner.setAdapter(profilesAdapter);
        final String basal = InsulinManager.getBasalProfile().getDisplayName();
        final String bolus = InsulinManager.getBolusProfile().getDisplayName();
        for (int i = 0; i < p.size(); i++) {
            if (p.get(i).equals(basal))
                basalSpinner.setSelection(i);
            if (p.get(i).equals(bolus))
                bolusSpinner.setSelection(i);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // when returning from the add/edit form, rebuild the list so it reflects any changes
        if (resumedOnce && !recreating) {
            recreating = true; // guard against an recreate()/onResume loop
            recreate();
            return;
        }
        recreating = false;
        resumedOnce = true;
    }

    public void profileCancelButton(View myview) {
        InsulinManager.LoadDisabledProfilesFromPrefs();
        finish();
    }

    public void profileAddButton(View myview) {
        startActivity(new android.content.Intent(this, InsulinProfileAddActivity.class));
    }

    private void showCustomProfileOptions(final Insulin i) {
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(i.getDisplayName())
                .setItems(new CharSequence[]{
                                getString(com.eveningoutpost.dexdrip.R.string.edit),
                                getString(com.eveningoutpost.dexdrip.R.string.delete)},
                        (dialog, which) -> {
                            if (which == 0) {
                                final android.content.Intent intent = new android.content.Intent(this, InsulinProfileAddActivity.class);
                                intent.putExtra(InsulinProfileAddActivity.EXTRA_PROFILE_NAME, i.getName());
                                startActivity(intent);
                            } else if (InsulinManager.deleteCustomProfile(i.getName())) {
                                JoH.static_toast_long(getString(com.eveningoutpost.dexdrip.R.string.insulin_profile_deleted));
                                // reload from prefs so basal/bolus pointers cannot reference the deleted profile
                                InsulinManager.LoadDisabledProfilesFromPrefs();
                                // rebuild the whole list view
                                recreate();
                            }
                        })
                .setNegativeButton(getString(com.eveningoutpost.dexdrip.R.string.cancel), null)
                .show();
    }

    public void profileSaveButton(View myview) {
        InsulinManager.saveDisabledProfilesToPrefs();
        finish();
    }

    public void profileUndoButton(View myview) {
        InsulinManager.LoadDisabledProfilesFromPrefs();
        for (Insulin i : InsulinManager.getAllProfiles())
            if (InsulinManager.isProfileEnabled(i))
                checkboxes.get(i).setChecked(true);
            else
                checkboxes.get(i).setChecked(false);

        ArrayList<String> p = new ArrayList<String>(profiles.keySet());
        final String basal = InsulinManager.getBasalProfile().getDisplayName();
        final String bolus = InsulinManager.getBolusProfile().getDisplayName();
        for (int i = 0; i < p.size(); i++) {
            if (p.get(i).equals(basal))
                basalSpinner.setSelection(i);
            if (p.get(i).equals(bolus))
                bolusSpinner.setSelection(i);
        }
    }
}

