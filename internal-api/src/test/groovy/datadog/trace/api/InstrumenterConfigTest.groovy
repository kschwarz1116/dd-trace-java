package datadog.trace.api

import datadog.trace.config.inversion.ConfigHelper
import datadog.trace.test.util.DDSpecification

import static datadog.environment.JavaVirtualMachine.isJavaVersionAtLeast
import static datadog.trace.api.config.TraceInstrumentationConfig.RUNTIME_CONTEXT_MAP_PER_STORE

class InstrumenterConfigTest extends DDSpecification {

  def strictness

  def setup(){
    strictness = ConfigHelper.get().configInversionStrictFlag()
    ConfigHelper.get().setConfigInversionStrict(ConfigHelper.StrictnessPolicy.TEST)
  }

  def cleanup(){
    ConfigHelper.get().setConfigInversionStrict(strictness)
  }

  def "verify integration config"() {
    setup:
    environmentVariables.set("DD_INTEGRATION_ORDER_ENABLED", "false")
    environmentVariables.set("DD_INTEGRATION_TEST_ENV_ENABLED", "true")
    environmentVariables.set("DD_TRACE_NEW_ENV_ENABLED", "false")
    environmentVariables.set("DD_INTEGRATION_DISABLED_ENV_ENABLED", "false")

    System.setProperty("dd.integration.order.enabled", "true")
    System.setProperty("dd.integration.test-prop.enabled", "true")
    System.setProperty("dd.integration.disabled-prop.enabled", "false")

    environmentVariables.set("DD_INTEGRATION_ORDER_MATCHING_SHORTCUT_ENABLED", "false")
    environmentVariables.set("DD_INTEGRATION_TEST_ENV_MATCHING_SHORTCUT_ENABLED", "true")
    environmentVariables.set("DD_INTEGRATION_NEW_ENV_MATCHING_SHORTCUT_ENABLED", "false")
    environmentVariables.set("DD_INTEGRATION_DISABLED_ENV_MATCHING_SHORTCUT_ENABLED", "false")

    System.setProperty("dd.integration.order.matching.shortcut.enabled", "true")
    System.setProperty("dd.integration.test-prop.matching.shortcut.enabled", "true")
    System.setProperty("dd.integration.disabled-prop.matching.shortcut.enabled", "false")

    expect:
    InstrumenterConfig.get().isIntegrationEnabled(integrationNames, defaultEnabled) == expected
    InstrumenterConfig.get().isIntegrationShortcutMatchingEnabled(integrationNames, defaultEnabled) == expected

    where:
    // spotless:off
    names                          | defaultEnabled | expected
    []                             | true           | true
    []                             | false          | false
    ["invalid"]                    | true           | true
    ["invalid"]                    | false          | false
    ["test-prop"]                  | false          | true
    ["test-env"]                   | false          | true
    ["disabled-prop"]              | true           | false
    ["disabled-env"]               | true           | false
    ["other", "test-prop"]         | false          | true
    ["other", "test-env"]          | false          | true
    ["order"]                      | false          | true
    ["test-prop", "disabled-prop"] | false          | true
    ["disabled-env", "test-env"]   | false          | true
    ["test-prop", "disabled-prop"] | true           | false
    ["disabled-env", "test-env"]   | true           | false
    ["new-env"] | true | false
    // spotless:on

    integrationNames = new TreeSet<>(names)
  }

  def setEnv(String key, String value) {
    environmentVariables.set(key, value)
  }

  def setSysProp(String key, String value) {
    System.setProperty(key, value)
  }

  def randomIntegrationEnabled() {
    return InstrumenterConfig.get().isIntegrationEnabled(["random"], true)
  }

  def "verify integration enabled hierarchy"() {
    when:
    // the below should have no effect
    setEnv("DD_RANDOM_ENABLED", "false")
    setSysProp("dd.random.enabled", "false")

    then:
    randomIntegrationEnabled() == true

    when:
    setEnv("DD_INTEGRATION_RANDOM_ENABLED", "false")

    then:
    randomIntegrationEnabled() == false

    when:
    setEnv("DD_TRACE_INTEGRATION_RANDOM_ENABLED", "true")

    then:
    randomIntegrationEnabled() == true

    when:
    setEnv("DD_TRACE_RANDOM_ENABLED", "false")

    then:
    randomIntegrationEnabled() == false

    // assert all system properties take precedence over all env vars
    when:
    setSysProp("dd.integration.random.enabled", "true")

    then:
    randomIntegrationEnabled() == true

    when:
    setSysProp("dd.trace.integration.random.enabled", "false")

    then:
    randomIntegrationEnabled() == false

    when:
    setSysProp("dd.trace.random.enabled", "true")

    then:
    randomIntegrationEnabled() == true
  }

  def "valid resolver presets"() {
    setup:
    injectSysConfig("resolver.cache.config", preset)

    expect:
    InstrumenterConfig.get().resolverOutliningEnabled == outlining

    where:
    // spotless:off
    preset    | outlining
    'LARGE'   | true
    'SMALL'   | true
    'DEFAULT' | true
    'LEGACY'  | false
    // spotless:on
  }

  def "invalid resolver presets"() {
    setup:
    injectSysConfig("resolver.cache.config", preset)

    expect:
    InstrumenterConfig.get().resolverOutliningEnabled

    where:
    preset << ['INVALID', '']
  }

  def "shared context storage is default from Java 25"() {
    expect:
    InstrumenterConfig.get().isRuntimeContextMapPerStore() == !isJavaVersionAtLeast(25)
  }

  def "context map per store configuration: #scenario"() {
    setup:
    if (environment != null) {
      injectEnvConfig("DD_TRACE_RUNTIME_CONTEXT_MAP_PER_STORE", environment)
    }
    if (property != null) {
      injectSysConfig(RUNTIME_CONTEXT_MAP_PER_STORE, property)
    }

    expect:
    InstrumenterConfig.get().isRuntimeContextMapPerStore() == expected

    where:
    // spotless:off
    scenario                              | property | environment | expected
    "Enable map per store with property"  | "true"   | null        | true
    "Disable map per store with property" | "false"  | null        | false
    "Enable map per store with env"       | null     | "true"      | true
    "Disable map per store with env"      | null     | "false"     | false
    "Property true overrides env false"   | "true"   | "false"     | true
    "Property false overrides env true"   | "false"  | "true"      | false
    // spotless:on
  }

  def "appsec enabled = #input"() {
    setup:
    if (input != null) {
      injectSysConfig("appsec.enabled", input)
    }

    expect:
    InstrumenterConfig.get().getAppSecActivation() == expected

    where:
    input      | expected
    null       | ProductActivation.ENABLED_INACTIVE
    ""         | ProductActivation.ENABLED_INACTIVE
    "bad"      | ProductActivation.FULLY_DISABLED
    "false"    | ProductActivation.FULLY_DISABLED
    "0"        | ProductActivation.FULLY_DISABLED
    "true"     | ProductActivation.FULLY_ENABLED
    "1"        | ProductActivation.FULLY_ENABLED
    "inactive" | ProductActivation.ENABLED_INACTIVE
  }

  def "iast enabled = #input"() {
    setup:
    if (input != null) {
      injectSysConfig("iast.enabled", input)
    }

    expect:
    InstrumenterConfig.get().getIastActivation() == expected

    where:
    input      | expected
    null       | ProductActivation.FULLY_DISABLED
    ""         | ProductActivation.FULLY_DISABLED
    "bad"      | ProductActivation.FULLY_DISABLED
    "false"    | ProductActivation.FULLY_DISABLED
    "0"        | ProductActivation.FULLY_DISABLED
    "true"     | ProductActivation.FULLY_ENABLED
    "1"        | ProductActivation.FULLY_ENABLED
    "inactive" | ProductActivation.ENABLED_INACTIVE
  }
}
