const fs = require('node:fs');
const assert = require('node:assert/strict');
const test = require('node:test');
const ts = require('typescript');

require.extensions['.ts'] = (module, filename) => {
  const source = fs.readFileSync(filename, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
    fileName: filename,
  }).outputText;
  module._compile(output, filename);
};

const { classifyNavigation } = require('../src/shell/navigationPolicy.ts');
const { DEFAULT_WEB_URL, resolveWebUrl } = require('../src/shell/webUrl.ts');

test('development defaults to localhost and accepts configured HTTP origins', () => {
  assert.deepEqual(resolveWebUrl(undefined, true), {
    ok: true,
    origin: 'http://localhost:5173',
    url: `${DEFAULT_WEB_URL}/`,
  });
  assert.equal(resolveWebUrl('http://192.168.0.10:5173', true).ok, true);
});

test('production requires HTTPS and rejects malformed or credentialed URLs', () => {
  assert.deepEqual(resolveWebUrl(undefined, false), {
    ok: false,
    error: 'insecure-production-url',
  });
  assert.equal(resolveWebUrl('https://openmd.example', false).ok, true);
  assert.equal(resolveWebUrl('not a url', true).ok, false);
  assert.equal(resolveWebUrl('file:///tmp/openmd.html', true).ok, false);
  assert.equal(resolveWebUrl('https://user:secret@openmd.example', false).ok, false);
});

test('only the exact configured origin stays inside the WebView', () => {
  assert.deepEqual(
    classifyNavigation('https://app.openmd.example/learning/1', 'https://app.openmd.example'),
    {
      action: 'internal',
      url: 'https://app.openmd.example/learning/1',
    },
  );
  assert.equal(
    classifyNavigation('https://app.openmd.example.attacker.test', 'https://app.openmd.example')
      .action,
    'external',
  );
  assert.equal(
    classifyNavigation('https://api.openmd.example', 'https://app.openmd.example').action,
    'external',
  );
});

test('supported external schemes leave the WebView and unsafe schemes are blocked', () => {
  assert.equal(classifyNavigation('https://example.com', 'https://app.openmd.example').action, 'external');
  assert.equal(classifyNavigation('http://example.com', 'https://app.openmd.example').action, 'external');
  assert.equal(classifyNavigation('mailto:hello@example.com', 'https://app.openmd.example').action, 'external');
  assert.equal(classifyNavigation('tel:+821012345678', 'https://app.openmd.example').action, 'external');
  assert.equal(classifyNavigation('javascript:alert(1)', 'https://app.openmd.example').action, 'blocked');
  assert.equal(classifyNavigation('file:///tmp/openmd.html', 'https://app.openmd.example').action, 'blocked');
  assert.equal(classifyNavigation('data:text/html,hello', 'https://app.openmd.example').action, 'blocked');
  assert.equal(classifyNavigation('openmd://profile', 'https://app.openmd.example').action, 'blocked');
});
