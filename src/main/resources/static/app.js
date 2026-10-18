"use strict";

const $ = (id) => document.getElementById(id);

const escapeHtml = (s) => s.replace(/[&<>"']/g, (c) =>
  ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

async function api(path, options = {}) {
  const res = await fetch(path, options);
  if (res.status === 204) return null;
  const body = await res.json().catch(() => null);
  if (!res.ok) throw new Error(body?.detail || body?.title || `Request failed (${res.status})`);
  return body;
}

function setStatus(el, message, isError = false) {
  el.textContent = message;
  el.classList.toggle("error", isError);
}

async function loadStatus() {
  const { generationEnabled } = await api("api/status");
  const badge = $("mode");
  badge.textContent = generationEnabled ? "Claude answers enabled" : "Retrieval-only mode (no API key)";
  badge.classList.toggle("warn", !generationEnabled);
  badge.hidden = false;
}

async function loadDocuments() {
  const docs = await api("api/documents");
  const list = $("documents");
  list.replaceChildren();
  if (docs.length === 0) {
    list.innerHTML = '<li class="meta">No documents yet. Try the files in samples/.</li>';
    return;
  }
  for (const doc of docs) {
    const li = document.createElement("li");
    li.innerHTML = `<span><span class="name">${escapeHtml(doc.fileName)}</span><br>
      <span class="meta">${doc.chunks} chunks · ${doc.characters.toLocaleString()} chars</span></span>`;
    const remove = document.createElement("button");
    remove.className = "link";
    remove.title = "Remove";
    remove.textContent = "✕";
    remove.addEventListener("click", async () => {
      await api(`api/documents/${encodeURIComponent(doc.id)}`, { method: "DELETE" });
      loadDocuments();
    });
    li.append(remove);
    list.append(li);
  }
}

$("upload-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const status = $("upload-status");
  const file = $("file").files[0];
  if (!file) return;
  const form = new FormData();
  form.append("file", file);
  setStatus(status, "Indexing…");
  try {
    const doc = await api("api/documents", { method: "POST", body: form });
    setStatus(status, `Indexed ${doc.fileName} (${doc.chunks} chunks).`);
    e.target.reset();
    loadDocuments();
  } catch (err) {
    setStatus(status, err.message, true);
  }
});

function renderAnswer(text, mode) {
  const answer = $("answer");
  answer.classList.toggle("retrieval", mode !== "GENERATED");
  // Escape first, then turn [n] markers into links to the matching source.
  answer.innerHTML = escapeHtml(text).replace(/\[(\d{1,2})]/g,
    (m, n) => `<a href="#cite-${n}" data-cite="${n}">[${n}]</a>`);
}

function renderCitations(citations) {
  const list = $("citations");
  list.replaceChildren();
  for (const c of citations) {
    const li = document.createElement("li");
    li.id = `cite-${c.number}`;
    li.classList.toggle("cited", c.cited);
    li.innerHTML = `<span class="source">${escapeHtml(c.fileName)} · chunk ${c.chunkIndex + 1}</span>
      <span class="score">BM25 ${c.score.toFixed(2)}</span>
      <blockquote>${escapeHtml(c.snippet)}</blockquote>`;
    list.append(li);
  }
}

$("answer").addEventListener("click", (e) => {
  const n = e.target.dataset?.cite;
  if (!n) return;
  e.preventDefault();
  const item = $(`cite-${n}`);
  item?.scrollIntoView({ behavior: "smooth", block: "center" });
  item?.classList.add("flash");
  setTimeout(() => item?.classList.remove("flash"), 1200);
});

$("ask-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const button = e.target.querySelector("button");
  button.disabled = true;
  $("result").hidden = false;
  $("answer").textContent = "Thinking…";
  $("citations").replaceChildren();
  $("usage").textContent = "";
  try {
    const res = await api("api/ask", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ question: $("question").value, topK: Number($("topk").value) }),
    });
    renderAnswer(res.answer, res.mode);
    renderCitations(res.citations);
    if (res.usage) {
      $("usage").textContent = `${res.model} · ${res.usage.inputTokens} input / ${res.usage.outputTokens} output tokens`;
    }
  } catch (err) {
    $("answer").textContent = err.message;
  } finally {
    button.disabled = false;
  }
});

loadStatus().catch(() => {});
loadDocuments().catch((err) => setStatus($("upload-status"), err.message, true));
