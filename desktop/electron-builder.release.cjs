const packageJson = require('./package.json');

const publisher = process.env.WIZESTREAM_DESKTOP_WINDOWS_PUBLISHER;

module.exports = {
  ...packageJson.build,
  win: {
    ...packageJson.build.win,
    ...(publisher ? { publisherName: [publisher] } : {}),
  },
};
