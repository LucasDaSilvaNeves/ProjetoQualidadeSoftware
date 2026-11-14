'use strict';
// Autenticação HTTP Basic guardada só na sessionStorage (some ao fechar a aba).
let auth = sessionStorage.getItem('auth');
let me = JSON.parse(sessionStorage.getItem('me') || 'null');

const $ = (id) => document.getElementById(id);

function showAlert(message, type = 'danger') {
  const el = $('alert');
  el.className = 'alert alert-' + type;
  el.textContent = message; // textContent: nunca interpreta HTML vindo do servidor
}
function clearAlert() { $('alert').className = 'alert d-none'; }

async function api(path, options = {}) {
  const res = await fetch('/api' + path, {
    ...options,
    headers: { 'Content-Type': 'application/json', 'Authorization': auth, ...(options.headers || {}) },
  });
  if (res.status === 204) return null;
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 401) logout();
    const fields = body.errors ? ' ' + Object.values(body.errors).join('; ') : '';
    throw new Error((body.detail || 'Erro ao processar a requisição') + fields);
  }
  return body;
}

async function login(user, pass) {
  auth = 'Basic ' + btoa(unescape(encodeURIComponent(user + ':' + pass)));
  me = await api('/me');
  sessionStorage.setItem('auth', auth);
  sessionStorage.setItem('me', JSON.stringify(me));
}
function logout() {
  sessionStorage.clear(); auth = null; me = null; render();
}

function td(text) { const c = document.createElement('td'); c.textContent = text ?? ''; return c; }
function fmt(dt) { return dt ? dt.replace('T', ' ').slice(0, 16) : ''; }

function render() {
  const logged = !!(auth && me);
  $('login').classList.toggle('d-none', logged);
  $('app').classList.toggle('d-none', !logged);
  $('logout').classList.toggle('d-none', !logged);
  $('whoami').textContent = logged ? me.username + ' (' + me.role + ')' : '';
  document.querySelectorAll('.admin-only').forEach(e => e.classList.toggle('d-none', !(logged && me.role === 'ADMINISTRADOR')));
  $('reserveForm').classList.toggle('d-none', !(logged && (me.role === 'SOLICITANTE' || me.role === 'ADMINISTRADOR')));
  if (logged) { searchResources(); }
}

async function searchResources() {
  const p = new URLSearchParams();
  [['type', 'fType'], ['minCapacity', 'fCap'], ['location', 'fLoc'], ['competency', 'fComp']]
    .forEach(([k, id]) => { if ($(id).value) p.set(k, $(id).value); });
  if ($('fFrom').value && $('fTo').value) { p.set('availableFrom', $('fFrom').value); p.set('availableTo', $('fTo').value); }
  try {
    const list = await api('/resources?' + p.toString());
    const body = $('resourceRows'); body.replaceChildren();
    list.forEach(r => {
      const tr = document.createElement('tr');
      const cell = document.createElement('td');
      const cb = document.createElement('input');
      cb.type = 'checkbox'; cb.value = r.id; cb.className = 'form-check-input pick';
      cb.setAttribute('aria-label', 'Selecionar ' + r.name);
      cell.appendChild(cb); tr.appendChild(cell);
      [r.name, r.type, r.location, r.capacity, r.competencies, r.restricted ? 'Sim' : 'Não'].forEach(v => tr.appendChild(td(v)));
      body.appendChild(tr);
    });
  } catch (e) { showAlert(e.message); }
}

async function loadReservations() {
  try {
    const list = await api('/reservations');
    const body = $('reservationRows'); body.replaceChildren();
    list.forEach(r => {
      const tr = document.createElement('tr');
      tr.appendChild(td(r.id));
      tr.appendChild(td(r.resources.map(x => x.name).join(', ')));
      tr.appendChild(td(fmt(r.startTime)));
      tr.appendChild(td(fmt(r.endTime)));
      const st = document.createElement('td');
      const badge = document.createElement('span');
      badge.className = 'badge status-' + r.status; badge.textContent = r.status;
      st.appendChild(badge); tr.appendChild(st);
      const actions = document.createElement('td');
      const add = (label, path, cls) => {
        const b = document.createElement('button');
        b.className = 'btn btn-sm me-1 mb-1 btn-outline-' + cls; b.textContent = label;
        b.onclick = async () => {
          try { await api('/reservations/' + r.id + path, { method: 'POST' }); clearAlert(); loadReservations(); }
          catch (e) { showAlert(e.message); }
        };
        actions.appendChild(b);
      };
      if (me.role === 'RESPONSAVEL') {
        if (r.status === 'SOLICITADA') { add('Aprovar', '/approve', 'success'); add('Rejeitar', '/reject', 'danger'); }
        if (r.status === 'APROVADA') { add('Retirada', '/checkout', 'primary'); add('Não compareceu', '/no-show', 'secondary'); }
        if (r.status === 'EM_USO') { add('Devolução', '/return', 'success'); }
      }
      if (r.status === 'SOLICITADA' || r.status === 'APROVADA') { add('Cancelar', '/cancel', 'danger'); }
      tr.appendChild(actions); body.appendChild(tr);
    });
  } catch (e) { showAlert(e.message); }
}

$('loginForm').addEventListener('submit', async (ev) => {
  ev.preventDefault(); clearAlert();
  try { await login($('user').value, $('pass').value); $('pass').value = ''; render(); }
  catch (e) { auth = null; showAlert('Usuário ou senha inválidos'); }
});
$('logout').addEventListener('click', logout);
$('searchForm').addEventListener('submit', (ev) => { ev.preventDefault(); clearAlert(); searchResources(); });

$('reserveForm').addEventListener('submit', async (ev) => {
  ev.preventDefault(); clearAlert();
  const ids = [...document.querySelectorAll('.pick:checked')].map(c => c.value);
  if (!ids.length) return showAlert('Selecione ao menos um recurso');
  if (!$('fFrom').value || !$('fTo').value) return showAlert('Informe início e término na pesquisa acima');
  try {
    const r = await api('/reservations', { method: 'POST', body: JSON.stringify({
      resourceIds: ids, startTime: $('fFrom').value, endTime: $('fTo').value, purpose: $('purpose').value }) });
    showAlert('Reserva #' + r.id + ' criada com status ' + r.status, 'success');
    $('purpose').value = ''; searchResources();
  } catch (e) { showAlert(e.message); }
});

$('resourceForm').addEventListener('submit', async (ev) => {
  ev.preventDefault(); clearAlert();
  try {
    await api('/resources', { method: 'POST', body: JSON.stringify({
      type: $('rType').value, name: $('rName').value, location: $('rLoc').value,
      capacity: $('rCap').value ? Number($('rCap').value) : null, competencies: $('rComp').value,
      restricted: $('rRestricted').checked }) });
    showAlert('Recurso cadastrado', 'success'); $('resourceForm').reset();
  } catch (e) { showAlert(e.message); }
});

document.querySelectorAll('#tabs [data-tab]').forEach(btn => btn.addEventListener('click', () => {
  document.querySelectorAll('#tabs .nav-link').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  ['search', 'reservations', 'admin'].forEach(t => $('tab-' + t).classList.toggle('d-none', t !== btn.dataset.tab));
  if (btn.dataset.tab === 'reservations') loadReservations();
}));

render();
