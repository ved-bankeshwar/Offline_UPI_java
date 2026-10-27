# Contemporary issues

This demo is a classroom model of an offline payment, not a bank.

A mesh packet is a promise to pay. The money does not move until a phone with internet uploads the packet and the server commits the ledger row. Until that moment the same promise can sit on several phones. The server settles a ciphertext once. A second upload of the same bytes is a duplicate. A new nonce is a different payment, so two different packets can both debit the sender. That is the double-spend a real system has to close with the bank, not with the mesh.

The PIN is hashed and stored inside the encrypted instruction. Nothing compares that hash to a stored PIN. Anyone who can encrypt to the server's public key can build a debit. The public key is fetched from `/api/server-key` with no login.

`Object.finalize()` is obsolete and is not used. Connections and files are closed in `finally` or in try-with-resources.

The visit counter lives in the HTTP session cookie. That cookie only counts page loads for this demo. It is not authentication.
