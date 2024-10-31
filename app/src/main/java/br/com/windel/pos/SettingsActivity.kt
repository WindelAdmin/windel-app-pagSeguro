package br.com.windel.pos

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity


class SettingsActivity : AppCompatActivity() {

    private lateinit var buttonBack: Button
    private lateinit var editTextSerial: EditText
    private lateinit var editTextCodigoAtivacao: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)
        supportActionBar?.hide()

        val sharedPreferences = getSharedPreferences("windelConfig", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        buttonBack = findViewById(R.id.buttonExit);
        editTextSerial = findViewById(R.id.editTextSerial)
        editTextCodigoAtivacao = findViewById(R.id.editTextCodigoAtivacao)
        editTextSerial.filters += InputFilter.AllCaps()
        editTextCodigoAtivacao.filters += InputFilter.AllCaps()

        runOnUiThread {
            sharedPreferences.getString("terminalSerialNumber", "").let { editTextSerial.setText(it) }
            sharedPreferences.getString("codigoAtivacao", "").let { editTextCodigoAtivacao.setText(it) }
        }

        buttonBack.setOnClickListener {
            super.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    sharedPreferences.getString("terminalSerialNumber", "")?.let {  MainActivity.Companion.setSerial(it) }
                    sharedPreferences.getString("codigoAtivacao", "")?.let {  MainActivity.Companion.setCodigoAtivacao(it) }
                    finish()
                }
            })
            super.onBackPressedDispatcher.onBackPressed()
        }

        editTextSerial.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                MainActivity.setSerial(s.toString())
                editor.putString("terminalSerialNumber", s.toString())
                editor.apply()
            }
        })

        editTextCodigoAtivacao.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                MainActivity.setCodigoAtivacao(s.toString())
                editor.putString("codigoAtivacao", s.toString())
                editor.apply()
            }
        })

    }
}