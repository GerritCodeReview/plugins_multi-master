gerrit_plugin(
  name = 'multi-master',
  srcs = glob(['src/main/java/**/*.java']),
  resources = glob(['src/main/resources/**/*']),
  manifest_entries = [
    'Gerrit-Module: com.google.gerrit.plugins.multimaster.MultiMasterModule',
    'Gerrit-HttpModule: com.google.gerrit.plugins.multimaster.websession.FileCacheBasedWebSession$Module',
  ]
)
