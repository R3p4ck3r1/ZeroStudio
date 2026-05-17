package com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.src.app_package

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder

internal fun AndroidModuleTemplateBuilder.detailsActivityKt() = """
package ${data.packageName}

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class DetailsActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_details)
  }
}
""".trim()

internal fun AndroidModuleTemplateBuilder.detailsActivityJava() = """
package ${data.packageName};

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class DetailsActivity extends AppCompatActivity {
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_details);
  }
}
""".trim()
