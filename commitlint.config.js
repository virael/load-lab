module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [
      2, 'always',
      ['feat', 'fix', 'ci', 'docs', 'build', 'perf', 'refactor', 'test', 'style', 'chore', 'revert'],
    ],
  },
};