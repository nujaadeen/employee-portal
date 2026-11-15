# AI service gaps found while connecting Spring (prompt 06)

The AI service (`ai-service/`) was not changed. Each gap below has a workaround in Spring.

| Gap | Effect on the screens | Workaround |
|---|---|---|
| No list of jobs (`GET /v1/jobs` only returns one job by id) | The Knowledge base screen's "Indexing activity" needs the latest jobs | Spring stores each job it starts in `knowledge_activity` (job id, collection, kind, label, who) and asks the AI service for the status of the latest 10 |
| Documents have no "added by" and a Drive source has no name | "Uploaded by Daniel Okoye", "Google Drive folder Products/2026" | The label and the person are kept in `knowledge_activity`. The Drive folder name is not shown, only the folder id the admin typed |
| A collection has no list of sources (a Drive folder, a website) with their own sync time and "stale" state | The right-hand panel "Sources" of the Knowledge base screen | Spring shows what the documents tell: source types per collection and the documents list (`source_type`, `source_ref`, status). A per-source "synced 2 days ago / Stale" needs a source table in the AI service |
| `GET /v1/collections` has no per-document failure count | "2 files failed to index" | Spring counts `FAILED` documents from `GET /v1/collections/{code}/documents` (one call per collection) |
| The progress of a running index is only in the job, not on the collection | "Indexing 64%" on the collection row | Spring shows the status INDEXING on the row; the percentage comes from the activity list |
| Drive deletions are not synced | A file removed from Drive stays searchable | Delete it in the Knowledge base (DELETE document) |
| No endpoint to cancel a job | | none yet |
