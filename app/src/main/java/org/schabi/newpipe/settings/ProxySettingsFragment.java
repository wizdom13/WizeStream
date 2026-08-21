package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.network.AppProxySelector;
import org.schabi.newpipe.network.ProxyCredentialStore;

public final class ProxySettingsFragment extends BasePreferenceFragment {
    private EditTextPreference hostPreference;
    private EditTextPreference portPreference;
    private EditTextPreference passwordPreference;
    private ProxyCredentialStore credentialStore;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        final ListPreference modePreference = requirePreference(R.string.proxy_mode_key);
        hostPreference = requirePreference(R.string.proxy_host_key);
        portPreference = requirePreference(R.string.proxy_port_key);
        passwordPreference = requirePreference(R.string.proxy_password_key);
        credentialStore = new ProxyCredentialStore(requireContext());

        hostPreference.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_URI);
            editText.setSingleLine(true);
        });
        portPreference.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            editText.setSingleLine(true);
        });
        passwordPreference.setPersistent(false);
        passwordPreference.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            editText.setSingleLine(true);
        });
        passwordPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            final String password = String.valueOf(newValue);
            final boolean saved = password.isEmpty()
                    ? credentialStore.clearPassword()
                    : credentialStore.savePassword(password);
            if (!saved) {
                Toast.makeText(requireContext(), R.string.proxy_password_save_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            passwordPreference.setText(null);
            updatePasswordSummary();
            AppProxySelector.refreshInstalled();
            return false;
        });
        updatePasswordSummary();

        hostPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if (!isValidHost(String.valueOf(newValue))) {
                Toast.makeText(requireContext(), R.string.proxy_invalid_host,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        });
        portPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if (!isValidPort(String.valueOf(newValue))) {
                Toast.makeText(requireContext(), R.string.proxy_invalid_port,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        });
        modePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if (!getString(R.string.proxy_mode_disabled_value).equals(newValue)
                    && !isValidHost(hostPreference.getText())) {
                Toast.makeText(requireContext(), R.string.proxy_invalid_host,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            if (!getString(R.string.proxy_mode_disabled_value).equals(newValue)
                    && !isValidPort(portPreference.getText())) {
                Toast.makeText(requireContext(), R.string.proxy_invalid_port,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        });
    }

    private void updatePasswordSummary() {
        passwordPreference.setSummary(credentialStore.hasPassword()
                ? R.string.proxy_password_configured
                : R.string.proxy_password_not_configured);
    }

    static boolean isValidHost(final String host) {
        if (host == null) {
            return false;
        }
        final String value = host.trim();
        return !value.isEmpty() && !value.contains("://") && !value.contains("/")
                && value.chars().noneMatch(Character::isWhitespace);
    }

    static boolean isValidPort(final String port) {
        try {
            final int value = Integer.parseInt(port);
            return value >= 1 && value <= 65_535;
        } catch (final NumberFormatException ignored) {
            return false;
        }
    }
}
