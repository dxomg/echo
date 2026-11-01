package dev.brahmkshatriya.echo.utils.ui.legacy;
import android.app.Dialog;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatDialogFragment;

public class EchoBottomSheetDialogFragment extends AppCompatDialogFragment {
    @Override public Dialog onCreateDialog(Bundle state) {
        return new EchoBottomSheetDialog(requireContext(), getTheme());
    }
}
