import assert from 'node:assert/strict';

const state = await (await fetch('http://127.0.0.1:8101/state')).json();
assert.equal(state.inWorld, true);
assert.equal(state.screen, 'MultiButtonDialogScreen');
assert.ok(state.dialogButtons.length > 0);
for (const button of state.dialogButtons) {
  assert.equal(typeof button.label, 'string');
  if (button.command !== undefined) assert.ok(button.command.startsWith('/semiontd '));
}
console.log(`PASS: ${state.dialogButtons.length} read-only dialog buttons`);
