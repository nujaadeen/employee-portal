Follow CLAUDE.md. Update the frontend for Phase 2: a streaming AI assistant, publishing to LinkedIn, the Knowledge base screen and the Connected accounts screen. The backend (docs/api.md, docs/openapi.json) is ready. Do not change backend/ or ai-service/. Gaps go in docs/backend-gaps.md with a workaround. First regenerate the API types from docs/openapi.json and read design/screens/08-ai-assistant.png, 10-knowledge-base.png and 11-social-accounts.png and the matching files in design/html/. Keep docs/progress.md as a checklist of the 7 steps and commit after each.

1. REGENERATE AND NAVIGATE
- Regenerate the types and fix the compile errors. Add Knowledge base (needs ai:knowledge:manage) and Connected accounts (needs social:publish:company or social:publish:personal) under ASSISTANT in the sidebar.

2. STREAMING CHAT (screen 08)
- Send messages with fetch and a readable stream parser for server-sent events, because EventSource cannot send the bearer token. Render tokens as they arrive, with an accessible live region that does not read every token aloud. Show a Stop generating button that aborts the request. Keep the partial text. Show a retry control on errors, and a clear Assistant unavailable state for the ai-unavailable problem, the 429 limit with the wait time, and the pending-approval case.
- Sources: show the source chips from the sources event, and make [1] style citations in the text link to the matching chip. Add thumbs up and down on each answer (feedback endpoint) with an optional comment.
- Draft card: show headline, caption and hashtags in editable fields with a character counter for LinkedIn (3000 limit, a hint at 1300), saved through PATCH /api/ai/drafts/{id}. Keep the look of the sample draft card.
- The right panel keeps using GET /api/ai/access.

3. PUBLISH FLOW
- The Publish to picker lists channels from the API. LinkedIn channels are enabled only if the user has the permission and a CONNECTED connection. If not connected, show Connect and link to the accounts page. Instagram and Facebook are shown disabled with the text Coming later. Remove the Schedule button.
- Publish opens a confirm dialog that shows the exact text and the target accounts. After publishing show a result per channel with text and icon, not colour alone: Published with a View post link, Failed with the reason and what to do, Simulated (development only), and Unknown with the message to check LinkedIn first and not to publish again. A double click must not send two requests.
- Add Copy text and Open LinkedIn as the fallback when the user has no connection.

4. CONNECTED ACCOUNTS (screen 11)
- Cards for Company LinkedIn page, My LinkedIn, Instagram and Facebook as in the screen, with Connect, Reconnect and Disconnect (with a confirm). Connect calls the connect endpoint and redirects the browser to the returned URL. The /accounts page handles the return flag with a success or error message. Company connection actions show only for users with access:manage.
- The Publishing history table with paging, result pills with text, and the View post and Reconnect links.

5. KNOWLEDGE BASE (screen 10)
- Stats cards, the collections table and the detail panel. Add source opens a dialog with three tabs: Upload PDF (drag and drop, progress, size and type validation before sending), Google Drive folder (folder id and a link on where to find it), Website (list of URLs). Re-index and delete document need a confirm.
- Poll the job endpoint every 3 seconds while a job is running and stop when the page is left. Show Indexing with progress, Failed with the error list, and Ready. Role access chips can be toggled and saved, with a note that this decides who can search the collection. New collection validates the code in the form as the server does.
- Loading, empty and error states everywhere. Everything works down to 390px wide.

6. TESTS
- Vitest: the stream parser (split chunks, error event, abort), the publish result mapping, the character counter, permission-based navigation.
- Playwright against the running stack: aisha.rahman asks about a product and sees tokens stream and sources only from product, brand or campaign; she edits the draft, publishes with LinkedIn switched off and sees Simulated, and sees Instagram as Coming later; lena.fischer has no Knowledge base and no publish picker; daniel.okoye uploads a small PDF, sees Indexing then Ready, and the document count changes. Take screenshots into frontend/e2e/screenshots/. Use the fake LLM setting of the AI service for these tests so they are fast, and document how to switch it on.

7. VISUAL CHECK AND DOCS
- Compare screenshots at 1440px with screens 08, 10 and 11 and fix each difference or record why in docs/decisions.md. Update docs/run.md and frontend/README.md.

DONE WHEN npm run build, lint, unit tests and the Playwright run pass, you show screenshots of the three screens, and you report what you ran and what you could not run.