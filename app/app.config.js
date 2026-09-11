module.exports = ({ config }) => {
  const androidGoogleServicesFile = process.env.GOOGLE_SERVICES_JSON?.trim();
  const iosGoogleServicesFile = process.env.GOOGLE_SERVICES_PLIST?.trim();

  return {
    ...config,
    ios: {
      ...config.ios,
      ...(iosGoogleServicesFile ? { googleServicesFile: iosGoogleServicesFile } : {}),
    },
    android: {
      ...config.android,
      ...(androidGoogleServicesFile ? { googleServicesFile: androidGoogleServicesFile } : {}),
    },
  };
};
