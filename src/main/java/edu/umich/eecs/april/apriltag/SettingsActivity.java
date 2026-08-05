package edu.umich.eecs.april.apriltag;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

/**
 * An {@link AppCompatActivity} that presents the application settings via a
 * {@link PreferenceFragmentCompat}.
 */
public class SettingsActivity extends AppCompatActivity {

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static final Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener =
            (preference, value) -> {
                String stringValue = value.toString();

                if (preference instanceof ListPreference) {
                    // For list preferences, look up the correct display value in
                    // the preference's 'entries' list.
                    ListPreference listPreference = (ListPreference) preference;
                    int index = listPreference.findIndexOfValue(stringValue);
                    preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
                } else {
                    // For all other preferences, set the summary to the value's
                    // simple string representation.
                    preference.setSummary(stringValue);
                }
                return true;
            };

    /**
     * Binds a preference's summary to its value, and updates it immediately.
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        if (preference == null) {
            return;
        }
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager.getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    /**
     * Shows the AprilTag processing preferences.
     */
    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            setPreferencesFromResource(R.xml.pref_settings, rootKey);

            int nproc = Runtime.getRuntime().availableProcessors();
            EditTextPreference nthreads = findPreference("nthreads_value");
            if (nthreads != null) {
                nthreads.setDefaultValue(Integer.toString(nproc));
            }

            // Bind the summaries of List/EditText preferences to their values.
            bindPreferenceSummaryToValue(findPreference("decimation_list"));
            bindPreferenceSummaryToValue(findPreference("sigma_value"));
            bindPreferenceSummaryToValue(findPreference("nthreads_value"));
            bindPreferenceSummaryToValue(findPreference("tag_family_list"));
            bindPreferenceSummaryToValue(findPreference("max_hamming_error"));
        }
    }
}
