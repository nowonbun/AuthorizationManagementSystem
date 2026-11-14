const app = document.getElementById("app");
const state = {
  me: null,
  page: "dashboard",
  users: [],
  menus: [],
  contents: [],
  audits: [],
  message: "",
};
const escapeHtml = (v) =>
  String(v ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
async function api(path, method = "GET", body) {
  let headers = {};
  if (method !== "GET") {
    const c = await fetch("/api/csrf", { credentials: "same-origin" }).then(
      (r) => r.json(),
    );
    headers[c.headerName] = c.token;
    if (body && !(body instanceof URLSearchParams))
      headers["Content-Type"] = "application/json";
  }
  let r = await fetch("/api" + path, {
    method,
    headers,
    body:
      body instanceof URLSearchParams
        ? body
        : body
          ? JSON.stringify(body)
          : undefined,
    credentials: "same-origin",
  });
  if (!r.ok) {
    let data = await r.json().catch(() => ({}));
    throw new Error(
      data.message ||
        {
          401: "로그인이 필요합니다.",
          403: "권한이 없습니다.",
          409: "중복되거나 삭제할 수 없는 데이터입니다.",
        }[r.status] ||
        `요청 실패 (${r.status})`,
    );
  }
  if (r.status === 204) return null;
  return r.json();
}
const notify = (s, error = false) => {
  state.message = s;
  let n = document.querySelector("#notice");
  if (n) {
    n.textContent = s;
    n.className = "notice" + (error ? " error" : "");
    n.hidden = false;
  } else alert(s);
};
async function start() {
  try {
    state.me = await api("/me");
    await navigate("dashboard");
  } catch {
    renderLogin();
  }
}
function renderLogin() {
  app.innerHTML = `
    <div class="login">
      <div class="login-card">
        <div class="login-brand">
          <h1>권한 관리 시스템</h1>
          <p>안전하고 유연한<br>권한 관리로 더 나은 조직을 만듭니다.</p>
        </div>
        <form class="login-form" id="login-form">
          <div>
            <h1>시스템에 로그인하세요</h1>
            <p class="muted">계정으로 로그인하여 서비스를 이용합니다.</p>
          </div>
          <label>아이디<input name="username" required autocomplete="username"></label>
          <label>비밀번호<input type="password" name="password" required autocomplete="current-password"></label>
          <button class="primary">로그인</button>
          <div id="notice" hidden></div>
        </form>
      </div>
    </div>`;
  document.querySelector("#login-form").onsubmit = async (e) => {
    e.preventDefault();
    try {
      let f = new FormData(e.target);
      await api("/login", "POST", new URLSearchParams(f));
      state.me = await api("/me");
      await navigate("dashboard");
    } catch (err) {
      notify(err.message, true);
    }
  };
}
function shell() {
  let role = state.me.role;
  let links = [
    ["dashboard", "대시보드"],
    ["users", "사용자 관리"],
    ["menus", "메뉴 관리"],
    ["contents", "콘텐츠 관리"],
    ["permissions", "권한 관리"],
    ["audits", "감사 로그"],
  ].filter(
    ([p]) =>
      role === "SYSTEM_ADMIN" ||
      (role === "ADMIN"
        ? !["menus", "audits"].includes(p)
        : ["dashboard", "contents"].includes(p)),
  );
  app.innerHTML = `
    <div class="layout">
      <aside class="sidebar">
        <h2>♙ 권한 관리 시스템</h2>
        <nav class="nav">
          ${links
            .map(
              ([p, t]) => `
            <button data-page="${p}" class="${state.page === p ? "active" : ""}">
              ${t}
            </button>`,
            )
            .join("")}
        </nav>
        <div class="side-footer">
          ${escapeHtml(state.me.loginId)} · ${escapeHtml(role)}<br>
          <button id="password">비밀번호 변경</button>
          <button id="logout">로그아웃</button>
        </div>
      </aside>
      <main class="main">
        <div id="notice" class="notice" hidden></div>
        <div id="view"></div>
      </main>
    </div>`;
  document
    .querySelectorAll("[data-page]")
    .forEach((b) => (b.onclick = () => navigate(b.dataset.page)));
  document.querySelector("#logout").onclick = async () => {
    await api("/logout", "POST");
    state.me = null;
    renderLogin();
  };
  document.querySelector("#password").onclick = passwordModal;
}
async function navigate(page) {
  state.page = page;
  shell();
  let view = document.querySelector("#view");
  view.innerHTML = '<div class="card">불러오는 중...</div>';
  try {
    if (page === "dashboard") await dashboard();
    else if (page === "users") await usersPage();
    else if (page === "menus") await menusPage();
    else if (page === "contents") await contentsPage();
    else if (page === "permissions") await permissionsPage();
    else if (page === "audits") await auditsPage();
  } catch (e) {
    view.innerHTML = `<div class="notice error">${escapeHtml(e.message)}</div>`;
  }
}
const heading = (title, desc, button = "") =>
  `<div class="top"><div><h1>${title}</h1><div class="muted">${desc}</div></div>${button}</div>`;
function modal(title, fields, onSubmit) {
  let backdrop = document.createElement("div");
  backdrop.className = "modal-backdrop";
  backdrop.innerHTML = `
    <form class="modal">
      <h2>${escapeHtml(title)}</h2>
      <div class="form-grid">${fields}</div>
      <div class="modal-footer">
        <button type="button" id="cancel">취소</button>
        <button class="primary">저장</button>
      </div>
    </form>`;
  app.append(backdrop);
  backdrop.querySelector("#cancel").onclick = () => backdrop.remove();
  backdrop.onclick = (e) => {
    if (e.target === backdrop) backdrop.remove();
  };
  backdrop.querySelector("form").onsubmit = async (e) => {
    e.preventDefault();
    try {
      await onSubmit(new FormData(e.target));
      backdrop.remove();
      await navigate(state.page);
      notify("저장했습니다.");
    } catch (err) {
      notify(err.message, true);
    }
  };
  return backdrop;
}
const field = (label, name, value = "", type = "text", required = true) => `
  <label>${label}
    <input type="${type}" name="${name}" value="${escapeHtml(value)}"
           ${required ? "required" : ""}>
  </label>`;
const select = (label, name, values, value) => `
  <label>${label}
    <select name="${name}">
      ${values
        .map(
          (v) => `
        <option value="${escapeHtml(v)}" ${v === value ? "selected" : ""}>
          ${escapeHtml(v)}
        </option>`,
        )
        .join("")}
    </select>
  </label>`;
const val = (f, k) => f.get(k)?.toString().trim() || "";
async function dashboard() {
  let d = await api("/dashboard");
  const metricLabels = {
    users: "전체 사용자",
    admins: "관리자",
    menus: "메뉴",
    contents: "콘텐츠",
    myMenuCount: "내 메뉴",
  };
  document.querySelector("#view").innerHTML =
    heading("대시보드", "시스템 현황을 한눈에 확인하세요.") +
    `<div class="cards">${Object.entries(d)
      .map(
        ([k, v]) =>
          `<div class="card">
            <div class="muted">${metricLabels[k] || k}</div>
            <div class="metric">${v}</div>
          </div>`,
      )
      .join("")}</div>`;
}
async function usersPage() {
  state.users = await api("/users");
  document.querySelector("#view").innerHTML =
    heading(
      "사용자 관리",
      "시스템의 사용자 계정을 관리합니다.",
      '<button class="primary" id="add">+ 사용자 추가</button>',
    ) +
    `<div class="card">
      <div class="toolbar"><input id="search" placeholder="아이디, 이름, 이메일 검색"></div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>ID</th><th>아이디</th><th>이름</th><th>유형</th><th>이메일</th><th>상태</th><th>작업</th></tr>
          </thead>
          <tbody id="rows"></tbody>
        </table>
      </div>
    </div>`;
  document.querySelector("#add").onclick = () => userModal();
  document.querySelector("#search").oninput = drawUsers;
  drawUsers();
}
function drawUsers() {
  let q = document.querySelector("#search").value.toLowerCase();
  document.querySelector("#rows").innerHTML = state.users
    .filter((u) =>
      [u.loginId, u.name, u.email].some((x) => x.toLowerCase().includes(q)),
    )
    .map(
      (u) =>
        `<tr>
          <td>${u.id}</td>
          <td>${escapeHtml(u.loginId)}</td>
          <td>${escapeHtml(u.name)}</td>
          <td>${escapeHtml(u.role)}</td>
          <td>${escapeHtml(u.email)}</td>
          <td><span class="badge ${u.status === "ACTIVE" ? "" : "off"}">${escapeHtml(u.status)}</span></td>
          <td class="actions">
            <button data-edit="${u.id}">수정</button>
            <button data-del="${u.id}" class="danger">삭제</button>
          </td>
        </tr>`,
    )
    .join("");
  document
    .querySelectorAll("[data-edit]")
    .forEach(
      (b) =>
        (b.onclick = () =>
          userModal(state.users.find((u) => u.id == b.dataset.edit))),
    );
  document.querySelectorAll("[data-del]").forEach(
    (b) =>
      (b.onclick = async () => {
        if (confirm("사용자를 비활성 삭제할까요?")) {
          try {
            await api("/users/" + b.dataset.del, "DELETE");
            await navigate("users");
          } catch (e) {
            notify(e.message, true);
          }
        }
      }),
  );
}
function userModal(u) {
  let roles =
    state.me.role === "SYSTEM_ADMIN"
      ? ["USER", "ADMIN", "SYSTEM_ADMIN"]
      : ["USER"];
  modal(
    u ? "사용자 수정" : "사용자 등록",
    field("아이디", "loginId", u?.loginId || "") +
      field("이름", "name", u?.name || "") +
      field("이메일", "email", u?.email || "", "email") +
      field("소속", "department", u?.department || "", "text", false) +
      (u
        ? select(
            "상태",
            "status",
            ["ACTIVE", "INACTIVE", "LOCKED", "SUSPENDED"],
            u.status,
          )
        : field("비밀번호 (12자 이상)", "password", "", "password")) +
      select("사용자 유형", "role", roles, u?.role || "USER"),
    async (f) => {
      let data = {
        loginId: val(f, "loginId"),
        name: val(f, "name"),
        email: val(f, "email"),
        department: val(f, "department"),
        role: val(f, "role"),
      };
      if (u) {
        data.status = val(f, "status");
        await api("/users/" + u.id, "PUT", data);
      } else {
        data.password = val(f, "password");
        await api("/users", "POST", data);
      }
    },
  );
  if (u) document.querySelector(".modal [name=loginId]").disabled = true;
}
async function menusPage() {
  state.menus = await api("/menus");
  document.querySelector("#view").innerHTML =
    heading(
      "메뉴 관리",
      "시스템 메뉴를 생성하고 관리합니다.",
      '<button class="primary" id="add">+ 메뉴 추가</button>',
    ) +
    `<div class="card table-wrap">
      <table>
        <thead>
          <tr><th>ID</th><th>메뉴명</th><th>경로</th><th>상위</th><th>유형</th><th>상태</th><th>정렬</th><th>작업</th></tr>
        </thead>
        <tbody>
          ${state.menus
            .map(
              (m) => `
            <tr>
              <td>${m.id}</td>
              <td>${escapeHtml(m.name)}</td>
              <td>${escapeHtml(m.path)}</td>
              <td>${m.parentId || "-"}</td>
              <td>${escapeHtml(m.type)}</td>
              <td>${m.active ? "활성" : "비활성"}</td>
              <td>${m.sortOrder}</td>
              <td class="actions">
                <button data-edit="${m.id}">수정</button>
                <button class="danger" data-del="${m.id}">삭제</button>
              </td>
            </tr>`,
            )
            .join("")}
        </tbody>
      </table>
    </div>`;
  document.querySelector("#add").onclick = () => menuModal();
  document
    .querySelectorAll("[data-edit]")
    .forEach(
      (b) =>
        (b.onclick = () =>
          menuModal(state.menus.find((m) => m.id == b.dataset.edit))),
    );
  document.querySelectorAll("[data-del]").forEach(
    (b) =>
      (b.onclick = async () => {
        if (confirm("메뉴를 삭제할까요?")) {
          try {
            await api("/menus/" + b.dataset.del, "DELETE");
            navigate("menus");
          } catch (e) {
            notify(e.message, true);
          }
        }
      }),
  );
}
function menuModal(m) {
  let parents = [
    "",
    ...state.menus.filter((x) => x.id !== m?.id).map((x) => String(x.id)),
  ];
  modal(
    m ? "메뉴 수정" : "메뉴 추가",
    field("메뉴명", "name", m?.name || "") +
      field("경로 (URL)", "path", m?.path || "/") +
      select("유형", "type", ["MENU", "CONTENT", "LINK"], m?.type || "MENU") +
      `<label>상위 메뉴
        <select name="parentId">
          ${parents
            .map(
              (id) => `
            <option value="${id}" ${String(m?.parentId || "") === id ? "selected" : ""}>
              ${id ? escapeHtml(state.menus.find((x) => x.id == id)?.name || id) : "없음"}
            </option>`,
            )
            .join("")}
        </select>
      </label>` +
      field("아이콘", "icon", m?.icon || "", "text", false) +
      field("정렬 순서", "sortOrder", m?.sortOrder ?? 0, "number") +
      `<label class="check">
        <input type="checkbox" name="active" ${m?.active !== false ? "checked" : ""}> 활성
      </label>
      <label class="check">
        <input type="checkbox" name="visible" ${m?.visible !== false ? "checked" : ""}> 표시
      </label>`,
    async (f) => {
      let data = {
        name: val(f, "name"),
        path: val(f, "path"),
        type: val(f, "type"),
        parentId: val(f, "parentId") ? Number(val(f, "parentId")) : null,
        icon: val(f, "icon"),
        sortOrder: Number(val(f, "sortOrder")),
        active: f.has("active"),
        visible: f.has("visible"),
      };
      await api(m ? "/menus/" + m.id : "/menus", m ? "PUT" : "POST", data);
    },
  );
}
async function contentsPage() {
  let allMenus = await api("/my-menus");
  state.menus = allMenus.filter((m) => m.type === "CONTENT");
  state.contents = await api("/contents");
  let canCreate = state.me.role !== "USER";
  document.querySelector("#view").innerHTML =
    heading(
      "콘텐츠 관리",
      "메뉴에 표시될 콘텐츠를 관리합니다.",
      canCreate
        ? '<button class="primary" id="add">+ 콘텐츠 등록</button>'
        : "",
    ) +
    `<div class="card table-wrap">
      <table>
        <thead>
          <tr><th>ID</th><th>제목</th><th>메뉴</th><th>작성자</th><th>상태</th><th>등록일</th><th>작업</th></tr>
        </thead>
        <tbody>
          ${state.contents
            .map(
              (c) => `
            <tr>
              <td>${c.id}</td>
              <td>${escapeHtml(c.title)}</td>
              <td>${escapeHtml(allMenus.find((m) => m.id === c.menuId)?.name || c.menuId)}</td>
              <td>${c.authorId}</td>
              <td>${c.published ? "게시" : "임시"}</td>
              <td>${escapeHtml(c.createdAt?.slice(0, 10))}</td>
              <td class="actions">
                <button data-read="${c.id}">보기</button>
                ${
                  canCreate
                    ? `
                  <button data-edit="${c.id}">수정</button>
                  <button data-del="${c.id}" class="danger">삭제</button>`
                    : ""
                }
              </td>
            </tr>`,
            )
            .join("")}
        </tbody>
      </table>
    </div>`;
  if (canCreate) {
    document.querySelector("#add").onclick = () => contentModal();
    document
      .querySelectorAll("[data-edit]")
      .forEach(
        (b) =>
          (b.onclick = () =>
            contentModal(state.contents.find((c) => c.id == b.dataset.edit))),
      );
    document.querySelectorAll("[data-del]").forEach(
      (b) =>
        (b.onclick = async () => {
          if (confirm("콘텐츠를 삭제할까요?")) {
            try {
              await api("/contents/" + b.dataset.del, "DELETE");
              navigate("contents");
            } catch (e) {
              notify(e.message, true);
            }
          }
        }),
    );
  }
  document.querySelectorAll("[data-read]").forEach(
    (b) =>
      (b.onclick = () => {
        let c = state.contents.find((x) => x.id == b.dataset.read);
        let d = modal(
          c.title,
          `<div class="content-body span2">${escapeHtml(c.body)}</div>`,
          async () => {},
        );
        d.querySelector(".modal-footer").remove();
      }),
  );
}
function contentModal(c) {
  if (!state.menus.length) {
    notify("접근 가능한 콘텐츠 메뉴가 없습니다.", true);
    return;
  }
  modal(
    c ? "콘텐츠 수정" : "콘텐츠 등록",
    `<label class="span2">메뉴
      <select name="menuId">
        ${state.menus
          .map(
            (m) => `
          <option value="${m.id}" ${m.id === c?.menuId ? "selected" : ""}>
            ${escapeHtml(m.name)}
          </option>`,
          )
          .join("")}
      </select>
    </label>` +
      `<div class="span2">${field("제목", "title", c?.title || "")}</div>` +
      `<label class="span2">내용<textarea name="body" required>${escapeHtml(c?.body || "")}</textarea></label>` +
      `<label class="check"><input type="checkbox" name="published" ${c?.published ? "checked" : ""}> 게시</label>`,
    async (f) =>
      await api(c ? "/contents/" + c.id : "/contents", c ? "PUT" : "POST", {
        menuId: Number(val(f, "menuId")),
        title: val(f, "title"),
        body: val(f, "body"),
        published: f.has("published"),
      }),
  );
}
async function permissionsPage() {
  state.menus = await api("/permissions/menus");
  state.users = await api("/users");
  let admin = state.me.role === "SYSTEM_ADMIN";
  document.querySelector("#view").innerHTML =
    heading("권한 관리", "메뉴별 기능 권한과 관리자 위임 범위를 설정합니다.") +
    `<div class="card">
      <div class="toolbar">
        <select id="targetType">
          ${
            admin
              ? `
            <option value="role">역할 권한</option>
            <option value="scope">관리자 범위</option>`
              : ""
          }
          <option value="user">사용자별 권한</option>
        </select>
        <select id="target"></select>
      </div>
      <div class="permission-grid" id="grid"></div>
    </div>`;
  document.querySelector("#targetType").onchange = loadTargets;
  loadTargets();
}
function loadTargets() {
  let kind = document.querySelector("#targetType").value;
  let targets =
    kind === "role"
      ? ["ADMIN", "USER"].map((x) => ({ id: x, name: x }))
      : state.users
          .filter((u) =>
            kind === "scope" ? u.role === "ADMIN" : u.role !== "SYSTEM_ADMIN",
          )
          .map((u) => ({ id: u.id, name: u.name + " (" + u.loginId + ")" }));
  document.querySelector("#target").innerHTML = targets
    .map(
      (t) =>
        `<option value="${escapeHtml(t.id)}">${escapeHtml(t.name)}</option>`,
    )
    .join("");
  document.querySelector("#target").onchange = drawPermissions;
  drawPermissions();
}
async function drawPermissions() {
  let kind = document.querySelector("#targetType").value,
    target = document.querySelector("#target").value;
  if (!target) {
    document.querySelector("#grid").textContent = "대상이 없습니다.";
    return;
  }
  try {
    let grants = await api(`/permissions/${kind}/${target}`);
    let actions = ["VIEW", "CREATE", "UPDATE", "DELETE", "MANAGE"];
    document.querySelector("#grid").innerHTML =
      `<table><thead><tr><th>메뉴</th>${actions.map((x) => `<th>${x}</th>`).join("")}</tr></thead><tbody>${state.menus
        .map(
          (m) =>
            `<tr><td>${escapeHtml(m.name)}</td>${actions
              .map((action) => {
                let g = grants.find(
                  (g) => g.menuId === m.id && g.action === action,
                );
                let value =
                  kind === "user" ? g?.effect || "NONE" : g ? "ALLOW" : "NONE";
                return `<td>
                  <select data-menu="${m.id}" data-action="${action}">
                    <option value="NONE" ${value === "NONE" ? "selected" : ""}>없음</option>
                    <option value="ALLOW" ${value === "ALLOW" ? "selected" : ""}>허용</option>
                    ${
                      kind === "user"
                        ? `
                      <option value="DENY" ${value === "DENY" ? "selected" : ""}>차단</option>`
                        : ""
                    }
                  </select>
                </td>`;
              })
              .join("")}</tr>`,
        )
        .join("")}</tbody></table>`;
    document.querySelectorAll("#grid select").forEach(
      (s) =>
        (s.onchange = async () => {
          let effect = s.value === "NONE" ? null : s.value;
          let payload = {
            menuId: Number(s.dataset.menu),
            action: s.dataset.action,
            effect,
          };
          try {
            if (kind === "user") payload.userId = Number(target);
            if (kind === "role") payload.role = target;
            if (kind === "scope") {
              payload.adminId = Number(target);
              payload.enabled = effect === "ALLOW";
            }
            await api("/permissions/" + kind, "PUT", payload);
            notify("권한을 저장했습니다.");
          } catch (e) {
            notify(e.message, true);
            drawPermissions();
          }
        }),
    );
  } catch (e) {
    document.querySelector("#grid").textContent = e.message;
  }
}
async function auditsPage() {
  let data = await api("/audits");
  state.audits = data.content;
  document.querySelector("#view").innerHTML =
    heading("감사 로그", "주요 작업과 변경 이력을 확인합니다.") +
    `<div class="card table-wrap">
      <table>
        <thead>
          <tr><th>시간</th><th>작업자</th><th>대상</th><th>작업</th><th>리소스</th><th>변경 전</th><th>변경 후</th><th>IP</th></tr>
        </thead>
        <tbody>
          ${state.audits
            .map(
              (x) => `
            <tr>
              <td>${escapeHtml(x.createdAt)}</td>
              <td>${x.actorUserId ?? "-"}</td>
              <td>${x.targetUserId ?? "-"}</td>
              <td>${escapeHtml(x.action)}</td>
              <td>${escapeHtml(x.resourceType)} ${x.resourceId ?? ""}</td>
              <td>${escapeHtml(x.beforeValue)}</td>
              <td>${escapeHtml(x.afterValue)}</td>
              <td>${escapeHtml(x.ipAddress)}</td>
            </tr>`,
            )
            .join("")}
        </tbody>
      </table>
    </div>`;
}
function passwordModal() {
  modal(
    "비밀번호 변경",
    field("현재 비밀번호", "currentPassword", "", "password") +
      field("새 비밀번호 (12자 이상)", "newPassword", "", "password"),
    async (f) =>
      api("/me/password", "POST", {
        currentPassword: val(f, "currentPassword"),
        newPassword: val(f, "newPassword"),
      }),
  );
}
start();
