// Only used by bridge tests. It runs no PowerShell or Gradle commands.
const mode = process.argv[2];
if (mode === 'malformed') console.log('not JSON');
else if (mode === 'mismatch') console.log(JSON.stringify({ schemaVersion: 1, status: 'FAIL', exitCode: 1 }));
else if (mode === 'wrongroot') console.log(JSON.stringify({schemaVersion: 1, projectRoot: 'C:/wrong-project', status: 'PASS', exitCode: 0}));
else if (mode === 'hang') setInterval(() => {}, 1000);
else {
  console.log(JSON.stringify({ schemaVersion: 1, status: mode === 'fail' ? 'FAIL' : 'PASS', exitCode: mode === 'fail' ? 1 : 0 }));
  process.exitCode = mode === 'fail' ? 1 : 0;
}
