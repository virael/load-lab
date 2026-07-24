module.exports = {
  extends: ['@commitlint/config-conventional'],
  // Dependabot writes its own commit bodies (markdown tables of bumped packages) and we
  // cannot reformat them. Their table rows run 105-125 chars, which trips
  // body-max-line-length in the commitlint 19.x bundled by
  // wagoid/commitlint-github-action — newer commitlint exempts lines containing URLs,
  // that version does not. Skip bot commits outright rather than relaxing the length
  // rule for human commits too.
  ignores: [(message) => message.includes('Signed-off-by: dependabot[bot]')],
  rules: {
    'type-enum': [
      2, 'always',
      ['feat', 'fix', 'ci', 'docs', 'build', 'perf', 'refactor', 'test', 'style', 'chore', 'revert'],
    ],
  },
};