<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="mesh" uri="http://upimesh.demo/mesh" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>UPI Offline Mesh — Ledger</title>
    <style>
        :root {
            --ink: #1a2332;
            --muted: #5d6b7c;
            --line: #e3e8ef;
            --paper: #f4f7fb;
            --card: #ffffff;
            --teal: #0f6e6b;
            --teal-dark: #0b5553;
            --navy: #1d4e89;
            --navy-dark: #163e6e;
            --clay: #8d3d32;
            --ok: #1f7a4d;
            --ok-bg: #e7f6ee;
            --bad: #9a3412;
            --bad-bg: #fde8e4;
            --shadow: 0 10px 30px rgba(26, 35, 50, 0.06);
        }
        * { box-sizing: border-box; }
        body {
            margin: 0;
            color: var(--ink);
            background:
                radial-gradient(900px 420px at 100% -10%, #d9efe8 0%, transparent 55%),
                radial-gradient(700px 380px at -10% 0%, #d9e6f6 0%, transparent 50%),
                var(--paper);
            font-family: "Segoe UI", "Avenir Next", sans-serif;
        }
        .wrap { max-width: 1120px; margin: 0 auto; padding: 28px 20px 64px; }
        header.hero {
            display: flex;
            justify-content: space-between;
            gap: 24px;
            align-items: flex-end;
            margin-bottom: 22px;
        }
        h1 { margin: 0; font-size: 40px; letter-spacing: -0.03em; line-height: 1.05; }
        .lede { margin: 8px 0 0; max-width: 520px; color: var(--muted); font-size: 16px; }
        .session {
            min-width: 240px;
            background: var(--card);
            border: 1px solid var(--line);
            border-radius: 16px;
            box-shadow: var(--shadow);
            padding: 14px 16px;
        }
        .session p { margin: 0; color: var(--muted); font-size: 13px; }
        .session strong { display: block; margin-top: 2px; color: var(--ink); font-size: 18px; font-weight: 650; }
        .session .last { margin-top: 10px; }
        .banner {
            margin: 0 0 18px;
            padding: 12px 16px;
            border-radius: 12px;
            background: #fff;
            border: 1px solid #b7d4cf;
            color: var(--teal-dark);
        }
        .steps { display: grid; grid-template-columns: 1.4fr 1fr; gap: 16px; }
        .card {
            background: var(--card);
            border: 1px solid var(--line);
            border-radius: 18px;
            box-shadow: var(--shadow);
            padding: 18px;
        }
        h2 { margin: 0 0 14px; font-size: 15px; letter-spacing: 0.04em; text-transform: uppercase; color: var(--muted); }
        form.pay, .actions { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; }
        label { display: flex; flex-direction: column; gap: 6px; font-size: 12px; color: var(--muted); min-width: 120px; }
        input, select {
            font: inherit;
            color: var(--ink);
            background: #f8fafc;
            border: 1px solid #d5dee8;
            border-radius: 10px;
            padding: 10px 12px;
        }
        input:focus, select:focus { outline: 2px solid #b7ddd9; border-color: var(--teal); }
        button {
            font: inherit;
            font-weight: 650;
            color: white;
            background: var(--navy);
            border: 0;
            border-radius: 12px;
            padding: 11px 16px;
            cursor: pointer;
        }
        button:hover { background: var(--navy-dark); }
        button.quiet { background: var(--teal); }
        button.quiet:hover { background: var(--teal-dark); }
        button.warn { background: transparent; color: var(--clay); border: 1px solid #e7c6c1; }
        button.warn:hover { background: #fdf2f0; }
        .hint { margin: 12px 0 0; color: var(--muted); font-size: 13px; }
        section { margin-top: 22px; }
        .devices { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
        .device {
            background: var(--card);
            border: 1px solid var(--line);
            border-radius: 16px;
            padding: 14px;
            box-shadow: var(--shadow);
        }
        .device strong { display: block; margin-bottom: 8px; font-size: 14px; }
        .badge {
            display: inline-block;
            border-radius: 999px;
            padding: 3px 8px;
            font-size: 12px;
            background: #eef2f6;
            color: var(--muted);
        }
        .badge.on { background: var(--ok-bg); color: var(--ok); }
        .count { margin: 10px 0 0; font-size: 13px; color: var(--muted); }
        .count b { color: var(--ink); font-size: 20px; margin-right: 4px; }
        .panel { overflow-x: auto; border-radius: 18px; border: 1px solid var(--line); background: var(--card); box-shadow: var(--shadow); }
        table { width: 100%; border-collapse: collapse; }
        th, td { text-align: left; padding: 12px 14px; border-bottom: 1px solid var(--line); font-size: 14px; white-space: nowrap; }
        th { font-size: 11px; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); background: #f8fafc; }
        tr:last-child td { border-bottom: 0; }
        td.money, th.money { text-align: right; font-variant-numeric: tabular-nums; }
        .status {
            display: inline-block;
            border-radius: 999px;
            padding: 3px 8px;
            font-size: 12px;
            background: #eef2f6;
            color: var(--muted);
        }
        .status.SETTLED { background: var(--ok-bg); color: var(--ok); }
        .status.REJECTED { background: var(--bad-bg); color: var(--bad); }
        .foot { margin: 14px 2px 0; color: var(--muted); font-size: 13px; }
        code { font-family: Consolas, monospace; background: #fff; border: 1px solid var(--line); border-radius: 6px; padding: 1px 5px; }
        @media (max-width: 900px) {
            header.hero, .steps, .devices { display: block; }
            .session, .card, .device { margin-top: 12px; }
            h1 { font-size: 32px; }
        }
    </style>
</head>
<body>
<div class="wrap">
    <header class="hero">
        <div>
            <h1>UPI Offline Mesh</h1>
            <p class="lede">A phone encrypts a payment. Nearby phones carry the packet. The bridge uploads it, and the ledger moves the money once.</p>
        </div>
        <jsp:useBean id="pageBean" class="com.demo.upimesh.web.LedgerPage" scope="request"/>
        <aside class="session">
            <p>Visits this session</p>
            <strong><jsp:getProperty name="pageBean" property="visitCount"/></strong>
            <p class="last">Last result</p>
            <strong>
                <c:choose>
                    <c:when test="${not empty pageBean.lastOutcome}"><c:out value="${pageBean.lastOutcome}"/></c:when>
                    <c:otherwise>None yet</c:otherwise>
                </c:choose>
            </strong>
        </aside>
    </header>

    <c:if test="${not empty message}">
        <p class="banner"><c:out value="${message}"/></p>
    </c:if>

    <div class="steps">
        <section class="card">
            <h2>1. Inject a payment</h2>
            <form class="pay" method="post" action="${pageContext.request.contextPath}/ledger">
                <input type="hidden" name="action" value="settle"/>
                <label>From
                    <select name="senderVpa">
                        <option>alice@demo</option>
                        <option>bob@demo</option>
                        <option>carol@demo</option>
                        <option>dave@demo</option>
                    </select>
                </label>
                <label>To
                    <select name="receiverVpa">
                        <option>bob@demo</option>
                        <option>alice@demo</option>
                        <option>carol@demo</option>
                        <option>dave@demo</option>
                    </select>
                </label>
                <label>Amount
                    <input name="amount" value="100.00" required/>
                </label>
                <label>PIN
                    <input name="pin" type="password" value="1234" required/>
                </label>
                <button type="submit">Inject into mesh</button>
            </form>
            <p class="hint">This only places the packet on phone-alice. Balances stay put until the bridge uploads it.</p>
        </section>

        <section class="card">
            <h2>2. Carry, then upload</h2>
            <div class="actions">
                <form method="post" action="${pageContext.request.contextPath}/ledger">
                    <input type="hidden" name="action" value="gossip"/>
                    <button class="quiet" type="submit">Run gossip</button>
                </form>
                <form method="post" action="${pageContext.request.contextPath}/ledger">
                    <input type="hidden" name="action" value="flush"/>
                    <button class="quiet" type="submit">Flush bridge</button>
                </form>
                <form method="post" action="${pageContext.request.contextPath}/ledger">
                    <input type="hidden" name="action" value="reset"/>
                    <button class="warn" type="submit">Reset mesh</button>
                </form>
            </div>
            <p class="hint">Gossip copies the packet to every other phone. Flush settles whatever phone-bridge is holding.</p>
        </section>
    </div>

    <section>
        <h2>Phones</h2>
        <div class="devices">
            <c:forEach var="device" items="${devices}">
                <article class="device">
                    <strong><c:out value="${device.deviceId}"/></strong>
                    <c:choose>
                        <c:when test="${device.online}"><span class="badge on">Bridge</span></c:when>
                        <c:otherwise><span class="badge">Offline</span></c:otherwise>
                    </c:choose>
                    <p class="count"><b><c:out value="${device.packetCount}"/></b> packets</p>
                </article>
            </c:forEach>
        </div>
        <p class="foot">Idempotency cache: <c:out value="${cacheSize}"/></p>
    </section>

    <section>
        <h2>Accounts</h2>
        <div class="panel">
            <table>
                <thead><tr><th>VPA</th><th>Holder</th><th class="money">Balance</th></tr></thead>
                <tbody>
                <c:forEach var="account" items="${accounts}">
                    <tr>
                        <td><c:out value="${account.vpa}"/></td>
                        <td><c:out value="${account.holderName}"/></td>
                        <td class="money"><mesh:rupee value="${account.balance}"/></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty accounts}">
                    <tr><td colspan="3">No accounts yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </section>

    <section>
        <h2>Transactions</h2>
        <div class="panel">
            <table>
                <thead>
                <tr>
                    <th>ID</th><th>From</th><th>To</th><th class="money">Amount</th>
                    <th>Status</th><th>Bridge</th><th>Hops</th><th>Settled at</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="tx" items="${transactions}">
                    <tr>
                        <td><c:out value="${tx.id}"/></td>
                        <td><c:out value="${tx.senderVpa}"/></td>
                        <td><c:out value="${tx.receiverVpa}"/></td>
                        <td class="money"><mesh:rupee value="${tx.amount}"/></td>
                        <td><span class="status ${tx.status}"><c:out value="${tx.status}"/></span></td>
                        <td><c:out value="${tx.bridgeNodeId}"/></td>
                        <td><c:out value="${tx.hopCount}"/></td>
                        <td><c:out value="${tx.settledAt}"/></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty transactions}">
                    <tr><td colspan="8">No transactions yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
        <p class="foot">A bridge can also <code>POST</code> packet JSON to <code>/servlet/ingest</code>.</p>
    </section>
</div>
</body>
</html>
