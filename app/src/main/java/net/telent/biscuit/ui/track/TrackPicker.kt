package net.telent.biscuit.ui.track

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment


class TrackPicker(val trackPickerView : View) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
                .setTitle("Choose track")
                .setView(trackPickerView)
                .setPositiveButton("ok") { _, _ -> }
                .create()
    }
    companion object {
        const val TAG = "TrackPicker"
    }

}
