package com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder

internal fun AndroidModuleTemplateBuilder.mainActivityKt() = """
package ${data.packageName}

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    startActivity(Intent(this, GlassesMainActivity::class.java))
    finish()
  }
}
""".trim()

internal fun AndroidModuleTemplateBuilder.mainActivityJava() = """
package ${data.packageName};

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    startActivity(new Intent(this, GlassesMainActivity.class));
    finish();
  }
}
""".trim()
