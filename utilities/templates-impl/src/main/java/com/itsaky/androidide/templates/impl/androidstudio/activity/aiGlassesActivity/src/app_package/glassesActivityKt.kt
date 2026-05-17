package com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder

internal fun AndroidModuleTemplateBuilder.glassesActivityKt() = """
package ${data.packageName}

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class GlassesMainActivity : AppCompatActivity() {
  private lateinit var audioInterface: AudioInterface
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    audioInterface = AudioInterface(this, getString(R.string.hello_ai_glasses))
    lifecycle.addObserver(audioInterface)
    setContentView(TextView(this).apply { text = getString(R.string.hello_ai_glasses) })
  }
}
""".trim()

internal fun AndroidModuleTemplateBuilder.glassesActivityJava() = """
package ${data.packageName};

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class GlassesMainActivity extends AppCompatActivity {
  private AudioInterface audioInterface;
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    audioInterface = new AudioInterface(this, getString(R.string.hello_ai_glasses));
    getLifecycle().addObserver(audioInterface);
    TextView textView = new TextView(this);
    textView.setText(getString(R.string.hello_ai_glasses));
    setContentView(textView);
  }
}
""".trim()
