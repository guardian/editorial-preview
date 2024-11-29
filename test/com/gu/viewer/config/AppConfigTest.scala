package com.gu.viewer.config

import lib.ConfigHelpers
import org.mockito.MockitoSugar
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers

class AppConfigTest extends AnyFunSuite with Matchers with MockitoSugar with ConfigHelpers {

  test ("can construct with DEV stage") {
    val instance: AppConfig = devAppConfig
    instance.app must be ("viewer")
    instance.pandaDomain must be ("local.dev-gutools.co.uk")
    instance.pandaSettingsFileKey must be("local.dev-gutools.co.uk.settings")
    instance.pandaAuthCallback must be ("https://viewer.local.dev-gutools.co.uk/oauthCallback")
    instance.stage must be ("DEV")
  }

  test("can construct with PROD stage") {
    val instance = prodAppConfig
    instance.app must be("viewer")
    instance.pandaDomain must be("gutools.co.uk")
    instance.pandaSettingsFileKey must be("gutools.co.uk.settings")
    instance.pandaAuthCallback must be("https://viewer.gutools.co.uk/oauthCallback")
    instance.stage must be ("PROD")
  }
}

