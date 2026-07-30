let conventional;
try {
  conventional = (await import("@commitlint/config-conventional")).default;
} catch (error) {
  if (error == null || error.code !== "ERR_MODULE_NOT_FOUND") {
    throw error;
  }
  const toolchainConventional = (
    await import("/opt/quality/node-tools/node_modules/@commitlint/config-conventional/lib/index.js")
  ).default;
  const parserName = "conventional-changelog-conventionalcommits";
  const parserPath =
    "/opt/quality/node-tools/node_modules/conventional-changelog-conventionalcommits/src/index.js";
  const parserFactory = (await import(parserPath)).default;
  conventional = {
    ...toolchainConventional,
    parserPreset: {
      name: parserName,
      path: parserPath,
      parserOpts: parserFactory().parser,
    },
  };
}

export default conventional;
