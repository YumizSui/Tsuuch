package jp.ac.titech.itpro.sdl.furui.tsuuch

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment


class StartDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            super.onCreate(savedInstanceState)

            return AlertDialog.Builder(getActivity())
            .setTitle("\uD83D\uDE8Bフォアグラウンドで動作中\uD83D\uDE8B")
            .setMessage("位置情報はフォアグラウンドで取得されています。\n位置情報の取得を停止するときはアプリを終了するか、STOPボタンを押してください。")
            .setPositiveButton("OK", null)
            .create()
    }

    override fun onPause() {
        super.onPause()
        dismiss()
    }
}
