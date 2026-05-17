package com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.src.app_package

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder

internal fun AndroidModuleTemplateBuilder.mainActivityKt() = """
package ${data.packageName}

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    startActivity(Intent(this, DetailsActivity::class.java))
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
    setContentView(R.layout.activity_main);
    startActivity(new Intent(this, DetailsActivity.class));
  }
}
""".trim()
