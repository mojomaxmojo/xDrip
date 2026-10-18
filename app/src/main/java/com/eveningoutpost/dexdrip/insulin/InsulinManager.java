package com.eveningoutpost.dexdrip.insulin;

import android.util.Log;

import androidx.annotation.Keep;

import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;
import com.eveningoutpost.dexdrip.xdrip;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.Expose;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class InsulinManager {
    private static final String TAG = "InsulinManager";
    public static final String CUSTOM_PROFILES_PREF = "custom_insulin_profiles_json";
    private static ArrayList<Insulin> profiles;
    private static volatile Insulin basalProfile, bolusProfile;

    @Keep
    class insulinDataWrapper {
        @Expose
        public ArrayList<insulinData> profiles;

        insulinDataWrapper() {
            profiles = new ArrayList<insulinData>();
        }

        public ArrayList<Insulin> getInsulinProfiles() {
            if (!checkUniquenessPPN())
                return null;
            ArrayList<Insulin> ret = new ArrayList<Insulin>();
            for (insulinData d : profiles) {
                final Insulin insulin = createInsulin(d);
                if (insulin == null) return null;
                ret.add(insulin);
            }
            return ret;
        }

        private Boolean checkUniquenessPPN() {
            Log.d(TAG, "checking for uniqueness");
            ArrayList<String> PPNs = new ArrayList<String>();
            for (insulinData d : profiles)
                for (String ppn : d.PPN)
                    if (PPNs.contains(ppn)) {
                        Log.d(TAG, "pharmacy product number duplicated " + ppn + ". That's not allowed!");
                        return false;
                    } else PPNs.add(ppn);
            Log.d(TAG, "pharmacy product numbers unique");
            return true;
        }
    }

    @Keep
    class insulinCurve {
        @Expose
        public String type;
        @Expose
        public JsonObject data;
    }

    @Keep
    class insulinData {
        @Expose
        public String displayName;
        @Expose
        public String name;
        @Expose
        public ArrayList<String> PPN;
        @Expose
        public String concentration;
        @Expose
        public insulinCurve Curve;
    }

    // shared factory converting json profile data to an Insulin instance
    private static Insulin createInsulin(insulinData d) {
        switch (d.Curve.type.toLowerCase()) {
            case "linear trapezoid":
                final Insulin insulin = new LinearTrapezoidInsulin(d.name, d.displayName, d.PPN, d.concentration, d.Curve.data);
                Log.d(TAG, "initialized linear trapezoid insulin " + d.displayName);
                return insulin;
            default:
                Log.d(TAG, "UNKNOWN Curve-Type " + d.Curve.type);
                return null;
        }
    }

    // public read-only access to the raw custom profile json (used by the profile add/edit form)
    public static JsonArray getCustomProfilesJsonPublic() {
        return getCustomProfilesJson();
    }

    // get the raw json array of user-created custom profiles stored in preferences
    private static JsonArray getCustomProfilesJson() {
        try {
            final JsonElement element = new JsonParser().parse(Pref.getString(CUSTOM_PROFILES_PREF, "[]"));
            if (element != null && element.isJsonArray()) return element.getAsJsonArray();
        } catch (Exception e) {
            Log.e(TAG, "Could not parse custom insulin profiles: " + e);
        }
        return new JsonArray();
    }

    // append user-created custom profiles (from prefs) to the supplied list of predefined profiles
    private static void loadCustomProfiles(ArrayList<Insulin> list) {
        try {
            final JsonArray array = getCustomProfilesJson();
            final Gson gson = new Gson();
            for (JsonElement element : array) {
                final insulinData d = gson.fromJson(element, insulinData.class);
                if (d == null || d.Curve == null || d.Curve.type == null) continue;
                final Insulin insulin = createInsulin(d);
                if (insulin != null) list.add(insulin);
            }
            if (array.size() > 0)
                Log.d(TAG, "Loaded " + array.size() + " custom insulin profiles from prefs");
        } catch (Exception e) {
            Log.e(TAG, "Got exception loading custom insulin profiles: " + e);
        }
    }

    // add or update a user-defined profile, returns error message or null on success
    public static String saveCustomProfile(String name, String displayName, String concentration,
                                           String ppnCsv, String onset, String peak, String duration) {
        try {
            name = name == null ? "" : name.trim();
            displayName = displayName == null || displayName.trim().isEmpty() ? name : displayName.trim();
            concentration = concentration == null || concentration.trim().isEmpty() ? "U100" : concentration.trim();
            if (name.isEmpty())
                return "Please enter a profile name";
            if (getProfile(name) != null)
                return "A profile with this name already exists";
            final long onsetV = Long.parseLong(onset.trim());
            final long durationV = Long.parseLong(duration.trim());
            if (onsetV < 0 || durationV <= 0)
                return "Onset must be >= 0 and duration > 0 (minutes)";
            long t1, t2;
            final String peakStr = peak.trim();
            if (peakStr.contains("-")) {
                t1 = Long.parseLong(peakStr.split("-")[0].trim());
                t2 = Long.parseLong(peakStr.split("-")[1].trim());
            } else {
                t1 = t2 = Long.parseLong(peakStr);
            }
            if (t1 < onsetV || t2 < t1 || t2 > durationV)
                return "Peak must be between onset and duration";

            final JsonObject curve = new JsonObject();
            curve.addProperty("type", "linear trapezoid");
            final JsonObject data = new JsonObject();
            data.addProperty("onset", String.valueOf(onsetV));
            data.addProperty("peak", peakStr);
            data.addProperty("duration", String.valueOf(durationV));
            curve.add("data", data);

            final JsonObject obj = new JsonObject();
            obj.addProperty("name", name);
            obj.addProperty("displayName", displayName);
            obj.addProperty("concentration", concentration);
            final JsonArray ppnArray = new JsonArray();
            if (ppnCsv != null)
                for (String ppn : ppnCsv.split("[,;]")) {
                    final String trimmed = ppn.trim();
                    if (!trimmed.isEmpty()) ppnArray.add(trimmed);
                }
            if (ppnArray.size() == 0) ppnArray.add(name + "-" + concentration);
            obj.add("PPN", ppnArray);
            obj.add("Curve", curve);

            final JsonArray array = getCustomProfilesJson();
            // replace existing custom profile with the same name
            final JsonArray filtered = new JsonArray();
            for (JsonElement element : array) {
                final JsonObject existing = element.getAsJsonObject();
                if (!name.equalsIgnoreCase(existing.has("name") ? existing.get("name").getAsString() : ""))
                    filtered.add(existing);
            }
            filtered.add(obj);
            Pref.setString(CUSTOM_PROFILES_PREF, filtered.toString());
            Log.d(TAG, "Saved custom insulin profile: " + name);
            profiles = null; // force reload on next access
            return null;
        } catch (NumberFormatException e) {
            return "Onset, peak and duration must be numbers (minutes)";
        } catch (Exception e) {
            Log.e(TAG, "Error saving custom insulin profile: " + e);
            return "Error saving profile: " + e.toString();
        }
    }

    // delete a user-defined custom profile by name, returns true if something was deleted
    public static boolean deleteCustomProfile(String name) {
        try {
            final JsonArray array = getCustomProfilesJson();
            final JsonArray filtered = new JsonArray();
            boolean found = false;
            for (JsonElement element : array) {
                final JsonObject existing = element.getAsJsonObject();
                final String existingName = existing.has("name") ? existing.get("name").getAsString() : "";
                if (name.equalsIgnoreCase(existingName)) {
                    found = true;
                } else {
                    filtered.add(existing);
                }
            }
            if (found) {
                Pref.setString(CUSTOM_PROFILES_PREF, filtered.toString());
                Log.d(TAG, "Deleted custom insulin profile: " + name);
                profiles = null; // force reload on next access
            }
            return found;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting custom insulin profile: " + e);
            return false;
        }
    }

    // is this profile user-defined (custom) rather than predefined?
    public static boolean isCustomProfile(String name) {
        if (name == null) return false;
        for (JsonElement element : getCustomProfilesJson()) {
            final JsonObject existing = element.getAsJsonObject();
            if (existing.has("name") && name.equalsIgnoreCase(existing.get("name").getAsString()))
                return true;
        }
        return false;
    }

    private static String readTextFile(InputStream inputStream) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        if (inputStream != null) {
            byte buf[] = new byte[1024];
            int len;
            try {
                while ((len = inputStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, len);
                }
                outputStream.close();
                inputStream.close();
            } catch (IOException e) {

            }
        }
        return outputStream.toString();
    }

    private static void initializeInsulinManager(InputStream in_s) {
        Log.d(TAG, "Initialize insulin profiles");
        insulinDataWrapper iDW;
        try {
            String input = readTextFile(in_s);
            Log.d(TAG,"read text bytes: " + input.length());
            Gson gson = new Gson();
            iDW = gson.fromJson(input, insulinDataWrapper.class);
            profiles = iDW.getInsulinProfiles();
            if (profiles == null) profiles = new ArrayList<Insulin>();
            loadCustomProfiles(profiles);
            Log.d(TAG, "Loaded Insulin Profiles: " + Integer.toString(profiles.size()));
            LoadDisabledProfilesFromPrefs();
            Log.d(TAG, "InsulinManager initialized from config file and Prefs");
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "Got exception during insulin load: " + e.toString());
        }
    }

    private static void checkInitialized() {
        if (profiles == null) {
            getDefaultInstance();
        }
    }

    // populate the data set with predefined resource as otherwise the static reference could be lost
    // as we are not really safely handling it
    public static ArrayList<Insulin> getDefaultInstance() {
        return getInstance(xdrip.getAppContext().getResources().openRawResource(R.raw.insulin_profiles));
    }

    // before this can be public, the issue of what to do if profiles is null needs to be resolved.
    private static ArrayList<Insulin> getInstance(InputStream in_s) {
        initializeInsulinManager(in_s);
        return profiles;
    }

    public static Insulin getBasalProfile() {
        return basalProfile;
    }

    public static void setBasalProfile(Insulin p) {
        basalProfile = p;
    }

    public static Insulin getBolusProfile() {
        checkInitialized();
        if (bolusProfile == null && profiles != null && !profiles.isEmpty()) {
            // profiles loaded but the pointer was never set (partial init / publish race):
            // fall back to the same default LoadDisabledProfilesFromPrefs() uses, so a legacy
            // dose never produces an empty injection list and IoB never silently reads zero.
            bolusProfile = profiles.get(0);
        }
        return bolusProfile;
    }

    public static void setBolusProfile(Insulin p) {
        bolusProfile = p;
    }

    public static ArrayList<Insulin> getAllProfiles() {
        if (profiles == null) {
            InsulinManager.getDefaultInstance(); // this entire feature needs a serious rework
        }
        return profiles;
    }

    public static Insulin getProfile(int i) {
        checkInitialized();
        if (profiles == null) {
            Log.d(TAG, "InsulinManager seems not load Profiles beforehand");
            return null;
        }
        ArrayList<Insulin> t = new ArrayList<Insulin>();
        for (Insulin ins : profiles)
            if (isProfileEnabled(ins))
                t.add(ins);
        if (i >= t.size())
            return null;
        return t.get(i);
    }

    public static Insulin getProfile(String name) {
        checkInitialized();
        if (profiles == null) {
            Log.d(TAG, "InsulinManager seems not load Profiles beforehand");
            return null;
        }
        name = name.toLowerCase();
        // TODO consider hashmap maybe? how many could be iterated here?
        for (Insulin i : profiles) {
            if (i.getName().toLowerCase().equals(name))
                return i;
        }
        return null;
    }

    public static long getMaxEffect(Boolean enabled) {
        checkInitialized();
        long max = 0;
        for (Insulin i : profiles)
            if (!enabled || i.isEnabled())
                if (max < i.getMaxEffect())
                    max = i.getMaxEffect();
        return max;
    }

    public static Boolean isProfileEnabled(Insulin i) {
        return i.isEnabled();
    }

    public static void disableProfile(Insulin i) {
        if (isProfileEnabled(i) && (countEnabledProfiles() > 1))
            i.disable();
    }

    public static void enableProfile(Insulin i) {
        if (!isProfileEnabled(i) && (countEnabledProfiles() < 3))
            i.enable();
    }

    private static int countEnabledProfiles() {
        checkInitialized();
        int ret = 0;
        for (Insulin ins : profiles)
            if (isProfileEnabled(ins))
                ret++;
        return ret;
    }

    public static void LoadDisabledProfilesFromPrefs() {
        checkInitialized();
        for (Insulin i : profiles)
            i.enable();
        String json = Pref.getString("saved_disabled_insulinprofiles_json", "[]");
        Log.d(TAG, "Loaded disabled Insulin Profiles from Prefs: " + json);
        String[] disabled = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(json, String[].class);
        for (String d : disabled) {
            Insulin ins = getProfile(d);
            if (ins != null)
                disableProfile(ins);
        }
        String prof = Pref.getString("saved_basal_insulinprofiles", "");
        Log.d(TAG, "Loaded basal Insulin Profiles from Prefs: " + prof);
        basalProfile = getProfile(prof);
        if (basalProfile == null)
            basalProfile = profiles.get(0);
        prof = Pref.getString("saved_bolus_insulinprofiles", "");
        Log.d(TAG, "Loaded bolus Insulin Profiles from Prefs: " + prof);
        bolusProfile = getProfile(prof);
        if (bolusProfile == null)
            bolusProfile = profiles.get(0);
    }

    public static void saveDisabledProfilesToPrefs() {
        checkInitialized();
        ArrayList<String> disabled = new ArrayList<String>();
        for (Insulin i : profiles)
            if (!isProfileEnabled(i))
                disabled.add(i.getName());
        String json = new GsonBuilder().create().toJson(disabled);
        Pref.setString("saved_disabled_insulinprofiles_json", json);
        Log.d(TAG, "saved disabled Insulin Profiles to Prefs: " + json);
        if (basalProfile != null) {
            Pref.setString("saved_basal_insulinprofiles", basalProfile.getName());
            Log.d(TAG, "saved basal Insulin Profiles to Prefs: " + basalProfile.getName());
        }
        if (bolusProfile != null) {
            Pref.setString("saved_bolus_insulinprofiles", bolusProfile.getName());
            Log.d(TAG, "saved bolus Insulin Profiles to Prefs: " + bolusProfile.getName());
        }
    }
}
