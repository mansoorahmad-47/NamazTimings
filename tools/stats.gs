/**
 * Namaz Timings — the phone counter.
 *
 * Paste this into a Google Sheet: Extensions → Apps Script, delete whatever
 * is there, paste this in, Save. Then Deploy → New deployment → type "Web
 * app", "Execute as: Me", "Who has access: Anyone", Deploy. Copy the
 * https://script.google.com/macros/s/.../exec address it gives you and put it
 * in Stats.kt as the value of URL.
 *
 * Each phone sends a random number once a day. This keeps one row per phone,
 * with when it was first seen and when it was last seen. It never receives a
 * name, a location, a phone number, or anything else.
 */

var SHEET = 'phones';

function doGet(e) {
  try {
    var p = (e && e.parameter) || {};
    var id = String(p.id || '');

    // Reject anything that is not the shape of an install id. Without this a
    // stray request could write junk, or a very long string, into the sheet.
    if (!/^[A-Za-z0-9-]{8,64}$/.test(id)) return done();

    var version = String(p.v || '').slice(0, 20);
    var code = String(p.vc || '').slice(0, 10);

    // Two phones can easily ping in the same second. Without the lock, both
    // read "not present" and both append a row, and the count drifts upward
    // for ever.
    var lock = LockService.getScriptLock();
    lock.waitLock(20000);
    try {
      var ss = SpreadsheetApp.getActiveSpreadsheet();
      var sh = ss.getSheetByName(SHEET);
      if (!sh) {
        sh = ss.insertSheet(SHEET);
        sh.appendRow(['install id', 'first seen', 'last seen', 'version', 'code']);
        sh.setFrozenRows(1);
      }

      var now = new Date();
      var last = sh.getLastRow();
      var known = last > 1 ? sh.getRange(2, 1, last - 1, 1).getValues() : [];

      var row = -1;
      for (var i = 0; i < known.length; i++) {
        if (known[i][0] === id) { row = i + 2; break; }
      }

      if (row === -1) {
        sh.appendRow([id, now, now, version, code]);
      } else {
        sh.getRange(row, 3, 1, 3).setValues([[now, version, code]]);
      }
    } finally {
      lock.releaseLock();
    }
  } catch (err) {
    // Never fail loudly. A broken counter must not be visible to anyone
    // using the app, and the app ignores the reply in any case.
  }
  return done();
}

function done() {
  return ContentService.createTextOutput('ok');
}
