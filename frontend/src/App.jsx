import { useEffect, useState } from 'react'

// 배포 빌드는 VITE_API_URL=''(같은 오리진 상대경로), 로컬 개발은 기본값 유지
const API = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
const TOKEN_KEY = 'susuggang.token'
const EMAIL_KEY = 'susuggang.email'
const ORDERS_KEY = 'susuggang.orders'
// 토스 문서용 공개 테스트 클라이언트 키(비밀 아님) — 본인 키 발급 후엔 VITE_TOSS_CLIENT_KEY로 교체
const TOSS_CLIENT_KEY = import.meta.env.VITE_TOSS_CLIENT_KEY ?? 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm'

const CATEGORIES = [
  { icon: '🧶', label: '뜨개' },
  { icon: '🧸', label: '인형' },
  { icon: '🎀', label: '소품' },
  { icon: '🏺', label: '도예' },
  { icon: '🪡', label: '자수' },
  { icon: '📿', label: '비즈' },
  { icon: '🕯️', label: '캔들' },
  { icon: '📜', label: '문구' },
]

const THUMBS = [
  ['#fdecec', '🧸'],
  ['#eaf1fd', '🧶'],
  ['#fdf4e2', '🏺'],
  ['#f0eafd', '🎀'],
  ['#eafdF1', '🪡'],
  ['#fdeaf5', '📿'],
]

function thumbOf(title) {
  let h = 0
  for (const ch of title) h = (h * 31 + ch.codePointAt(0)) >>> 0
  return THUMBS[h % THUMBS.length]
}

function memberIdFromToken(token) {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return Number(JSON.parse(atob(payload)).sub)
  } catch {
    return null
  }
}

// CommonResponse 봉투 { status, msg, value }에서 실제 데이터(value)만 꺼낸다
async function readValue(res) {
  return (await res.json()).value
}

function App() {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY))
  const [userEmail, setUserEmail] = useState(() => localStorage.getItem(EMAIL_KEY))

  // 화면 전환: home | login | signup | register (라우터 없이 state로)
  const [view, setView] = useState('home')

  const [products, setProducts] = useState([])
  const [listState, setListState] = useState('loading')
  const [query, setQuery] = useState('')

  const [form, setForm] = useState({})
  const [formError, setFormError] = useState('')
  const [formNotice, setFormNotice] = useState('')
  const [formBusy, setFormBusy] = useState(false)

  const [orderingId, setOrderingId] = useState(null)
  const [payingId, setPayingId] = useState(null)
  // 토스 결제위젯: 결제 진행 중인 상품과 위젯 인스턴스 (view === 'payment')
  const [payTarget, setPayTarget] = useState(null)
  const [tossWidgets, setTossWidgets] = useState(null)
  // productId → { orderId, expiresAt(ms), state: 'reserved'|'paid'|'expired', msg }
  // localStorage에 보존 — 새로고침해도 예약(카운트다운)이 증발하지 않게
  const [orderCards, setOrderCards] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem(ORDERS_KEY)) ?? {}
    } catch {
      return {}
    }
  })
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    localStorage.setItem(ORDERS_KEY, JSON.stringify(orderCards))
  }, [orderCards])

  const [settlements, setSettlements] = useState([])
  const [settlementsState, setSettlementsState] = useState('loading')

  const [notifications, setNotifications] = useState([])
  const [notificationsState, setNotificationsState] = useState('loading')

  async function loadProducts() {
    setListState('loading')
    try {
      const res = await fetch(`${API}/products`)
      if (!res.ok) throw new Error()
      setProducts(await readValue(res))
      setListState('ready')
    } catch {
      setListState('error')
    }
  }

  useEffect(() => {
    loadProducts()
  }, [])

  async function loadNotifications() {
    setNotificationsState('loading')
    try {
      const res = await fetch(`${API}/notifications`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      if (res.status === 401 || res.status === 403) {
        logout()
        goto('login')
        return
      }
      if (!res.ok) throw new Error()
      setNotifications(await readValue(res))
      setNotificationsState('ready')
    } catch {
      setNotificationsState('error')
    }
  }

  async function loadSettlements() {
    setSettlementsState('loading')
    try {
      const res = await fetch(`${API}/settlements`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      if (res.status === 401 || res.status === 403) {
        logout()
        goto('login')
        return
      }
      if (!res.ok) throw new Error()
      setSettlements(await readValue(res))
      setSettlementsState('ready')
    } catch {
      setSettlementsState('error')
    }
  }

  // 예약 카운트다운 tick — RESERVED 카드가 있을 때만 1초 주기
  const hasReserved = Object.values(orderCards).some(c => c.state === 'reserved')
  useEffect(() => {
    if (!hasReserved) return
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [hasReserved])

  // 만료 감지: 시간이 다 된 RESERVED 카드는 expired로 전환하고 재고를 재조회
  useEffect(() => {
    const expired = Object.entries(orderCards).filter(
      ([, c]) => c.state === 'reserved' && c.expiresAt <= now,
    )
    if (expired.length === 0) return
    setOrderCards(prev => {
      const next = { ...prev }
      for (const [pid] of expired) {
        next[pid] = { ...next[pid], state: 'expired', msg: '예약이 만료되었습니다 · 재고는 곧 복구됩니다' }
      }
      return next
    })
    loadProducts()
  }, [now]) // eslint-disable-line react-hooks/exhaustive-deps

  function goto(nextView) {
    setView(nextView)
    setForm({})
    setFormError('')
    setFormNotice('')
    window.scrollTo(0, 0)
  }

  function logout() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(EMAIL_KEY)
    localStorage.removeItem(ORDERS_KEY)
    setToken(null)
    setUserEmail(null)
    setOrderCards({})
    goto('home')
  }

  function setField(name) {
    return e => setForm(prev => ({ ...prev, [name]: e.target.value }))
  }

  async function handleAuthSubmit(e) {
    e.preventDefault()
    setFormError('')
    setFormBusy(true)
    try {
      const res = await fetch(`${API}/auth/${view === 'signup' ? 'signup' : 'login'}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: form.email, password: form.password }),
      })
      if (!res.ok) throw new Error('http')
      if (view === 'signup') {
        setView('login')
        setForm({ email: form.email })
        setFormNotice('가입이 완료되었습니다. 로그인해 주세요.')
      } else {
        const data = await readValue(res)
        localStorage.setItem(TOKEN_KEY, data.token)
        localStorage.setItem(EMAIL_KEY, form.email)
        setToken(data.token)
        setUserEmail(form.email)
        goto('home')
      }
    } catch (err) {
      if (err instanceof TypeError) setFormError('서버에 연결할 수 없습니다.')
      else if (view === 'signup') setFormError('회원가입에 실패했습니다.')
      else setFormError('이메일 또는 비밀번호를 확인해 주세요.')
    } finally {
      setFormBusy(false)
    }
  }

  async function handleRegisterSubmit(e) {
    e.preventDefault()
    setFormError('')
    setFormBusy(true)
    try {
      const res = await fetch(`${API}/products`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          title: form.title,
          price: Number(form.price),
          quantity: Number(form.quantity),
          sellerId: memberIdFromToken(token),
        }),
      })
      if (res.status === 401 || res.status === 403) {
        logout()
        goto('login')
        return
      }
      if (!res.ok) throw new Error('http')
      await loadProducts()
      goto('home')
    } catch (err) {
      setFormError(err instanceof TypeError ? '서버에 연결할 수 없습니다.' : '작품 등록에 실패했습니다.')
    } finally {
      setFormBusy(false)
    }
  }

  async function handleOrder(product) {
    if (!token) {
      goto('login')
      return
    }
    setOrderingId(product.id)
    try {
      const res = await fetch(`${API}/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ productId: product.id }),
      })
      if (res.status === 201) {
        const data = await readValue(res)
        setOrderCards(prev => ({
          ...prev,
          [product.id]: {
            orderId: data.orderId,
            expiresAt: new Date(data.expiresAt).getTime(),
            state: 'reserved',
          },
        }))
        setNow(Date.now())
        setProducts(prev =>
          prev.map(p => (p.id === product.id ? { ...p, quantity: Math.max(0, p.quantity - 1) } : p)),
        )
      } else if (res.status === 409) {
        setOrderCards(prev => ({ ...prev, [product.id]: { state: 'error', msg: '품절되었습니다' } }))
        setProducts(prev => prev.map(p => (p.id === product.id ? { ...p, quantity: 0 } : p)))
      } else if (res.status === 401 || res.status === 403) {
        logout()
        goto('login')
      } else {
        setOrderCards(prev => ({ ...prev, [product.id]: { state: 'error', msg: '주문에 실패했습니다' } }))
      }
    } catch {
      setOrderCards(prev => ({ ...prev, [product.id]: { state: 'error', msg: '서버에 연결할 수 없습니다' } }))
    } finally {
      setOrderingId(null)
    }
  }

  // "결제하기" → 토스 결제위젯 화면으로 (mock confirm 대체 — 실제 확정은 successUrl 복귀 후 서버 승인이)
  function handleConfirm(product) {
    const card = orderCards[product.id]
    if (!card?.orderId) return
    setPayTarget(product)
    setTossWidgets(null)
    goto('payment')
  }

  // 결제 화면 진입 시 위젯 렌더 (결제수단 UI + 약관 UI)
  useEffect(() => {
    if (view !== 'payment' || !payTarget) return
    let cancelled = false
    async function render() {
      const toss = window.TossPayments(TOSS_CLIENT_KEY)
      const widgets = toss.widgets({ customerKey: window.TossPayments.ANONYMOUS })
      await widgets.setAmount({ currency: 'KRW', value: payTarget.price })
      await Promise.all([
        widgets.renderPaymentMethods({ selector: '#toss-payment-method', variantKey: 'DEFAULT' }),
        widgets.renderAgreement({ selector: '#toss-agreement', variantKey: 'AGREEMENT' }),
      ])
      if (!cancelled) setTossWidgets(widgets)
    }
    render().catch(() => setFormError('결제창을 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.'))
    return () => {
      cancelled = true
    }
  }, [view, payTarget]) // eslint-disable-line react-hooks/exhaustive-deps

  async function requestTossPayment() {
    const card = orderCards[payTarget.id]
    if (!tossWidgets || !card?.orderId) return
    const base = `${window.location.origin}${window.location.pathname}`
    try {
      await tossWidgets.requestPayment({
        // 토스 쪽 주문번호(문자열, 유니크) — 우리 주문 PK는 successUrl 쿼리(susuOrder)로 따로 돌려받는다
        orderId: `susuggang-${card.orderId}-${Date.now()}`,
        orderName: payTarget.title,
        successUrl: `${base}?susuOrder=${card.orderId}&susuProduct=${payTarget.id}`,
        failUrl: `${base}?susuFail=1&susuProduct=${payTarget.id}`,
      })
    } catch {
      setFormError('결제를 취소했습니다.')
    }
  }

  // successUrl/failUrl 복귀 처리 — 쿼리에 우리 파라미터가 있으면 서버 승인 호출
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    if (!params.get('susuProduct')) return
    window.history.replaceState({}, '', window.location.pathname)
    const productId = Number(params.get('susuProduct'))
    if (params.get('susuFail')) {
      setOrderCards(prev => ({
        ...prev,
        [productId]: { ...prev[productId], msg: `결제에 실패했습니다 (${params.get('code') ?? '취소'})` },
      }))
      return
    }
    confirmTossPayment(productId, params)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  async function confirmTossPayment(productId, params) {
    setPayingId(productId)
    try {
      const res = await fetch(`${API}/payments/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          orderId: Number(params.get('susuOrder')),
          tossOrderId: params.get('orderId'),
          paymentKey: params.get('paymentKey'),
          amount: Number(params.get('amount')),
        }),
      })
      if (res.ok) {
        setOrderCards(prev => ({
          ...prev,
          [productId]: { state: 'paid', msg: `결제 완료 · 주문번호 ${params.get('susuOrder')}` },
        }))
      } else if (res.status === 409) {
        setOrderCards(prev => ({
          ...prev,
          [productId]: { state: 'expired', msg: '결제 기한이 지났습니다 · 재고는 곧 복구됩니다' },
        }))
        loadProducts()
      } else if (res.status === 401 || res.status === 403) {
        logout()
        goto('login')
      } else {
        const body = await res.json().catch(() => null)
        setOrderCards(prev => ({
          ...prev,
          [productId]: { ...prev[productId], msg: body?.msg ?? '결제 승인에 실패했습니다' },
        }))
      }
    } catch {
      setOrderCards(prev => ({
        ...prev,
        [productId]: { ...prev[productId], msg: '서버에 연결할 수 없습니다 · 다시 시도해 주세요' },
      }))
    } finally {
      setPayingId(null)
    }
  }

  const visible = products.filter(p => p.title.toLowerCase().includes(query.trim().toLowerCase()))

  return (
    <>
      <header className="top">
        <div className="wrap top-inner">
          <button type="button" className="logo" onClick={() => goto('home')}>
            <span className="logo-mark">
              <em>수수</em>깡
            </span>
            <span className="logo-tag">수공예 판매 마켓</span>
          </button>

          <label className="search">
            <input
              placeholder="작품 검색"
              value={query}
              onChange={e => {
                setQuery(e.target.value)
                if (view !== 'home') setView('home')
              }}
            />
          </label>

          <div className="top-actions">
            {token ? (
              <>
                <button type="button" className="pill-btn" onClick={() => goto('register')}>
                  + 작품 등록
                </button>
                <button
                  type="button"
                  className="ghost-btn"
                  onClick={() => {
                    goto('notifications')
                    loadNotifications()
                  }}
                >
                  알림
                </button>
                <button
                  type="button"
                  className="ghost-btn"
                  onClick={() => {
                    goto('settlements')
                    loadSettlements()
                  }}
                >
                  정산 내역
                </button>
                <span className="account-email">{userEmail}</span>
                <button type="button" className="ghost-btn" onClick={logout}>
                  로그아웃
                </button>
              </>
            ) : (
              <>
                <button type="button" className="ghost-btn" onClick={() => goto('login')}>
                  로그인
                </button>
                <button type="button" className="pill-btn" onClick={() => goto('signup')}>
                  회원가입
                </button>
              </>
            )}
          </div>
        </div>
      </header>

      <main className="wrap">
        {view === 'home' && (
          <>
            <section className="hero">
              <div>
                <h1>작가의 손끝에서 온 한정 수량 작품</h1>
                <p>수수깡은 수공예 작품을 한정 수량으로 선착순 판매하는 마켓입니다.</p>
              </div>
              <div className="hero-art" aria-hidden="true">
                🧶
              </div>
            </section>

            <div className="cats">
              {CATEGORIES.map(c => (
                <button key={c.label} type="button" className="cat" onClick={() => setQuery(c.label)}>
                  <span className="cat-icon">{c.icon}</span>
                  {c.label}
                </button>
              ))}
            </div>

            {listState === 'loading' && <p className="status-box">작품을 불러오는 중입니다.</p>}

            {listState === 'error' && (
              <div className="status-box">
                <p>상품 목록을 불러오지 못했습니다. 서버 연결을 확인해 주세요.</p>
                <button type="button" className="ghost-btn" onClick={loadProducts}>
                  다시 시도
                </button>
              </div>
            )}

            {listState === 'ready' && visible.length === 0 && (
              <p className="status-box">
                {query ? `"${query}" 검색 결과가 없습니다.` : '등록된 작품이 없습니다.'}
              </p>
            )}

            {listState === 'ready' && visible.length > 0 && (
              <>
                <p className="count">
                  전체 작품 <span>{visible.length}</span>
                </p>
                <section className="grid">
                  {visible.map(p => {
                    const soldOut = p.quantity <= 0 || p.status === 'SOLD_OUT'
                    const card = orderCards[p.id]
                    const [bg, emoji] = thumbOf(p.title)
                    const remainMs = card?.state === 'reserved' ? Math.max(0, card.expiresAt - now) : 0
                    const mm = String(Math.floor(remainMs / 60000)).padStart(2, '0')
                    const ss = String(Math.floor((remainMs % 60000) / 1000)).padStart(2, '0')
                    return (
                      <article className="card" key={p.id}>
                        <div className="thumb" style={{ background: bg }} aria-hidden="true">
                          {emoji}
                          <span className={soldOut ? 'badge soldout' : 'badge'}>
                            {soldOut ? '품절' : `한정 ${p.quantity}개`}
                          </span>
                        </div>
                        <h2 className="card-title">{p.title}</h2>
                        <p className="card-price">₩{p.price.toLocaleString('ko-KR')}</p>
                        <p className="card-stock">{soldOut ? '다음 오픈을 기다려 주세요' : '선착순 판매 중'}</p>

                        {card?.state === 'reserved' ? (
                          <div className="pay-panel">
                            <div className="pay-row">
                              <span>주문번호 {card.orderId}</span>
                              <span className="pay-ttl">
                                {mm}:{ss} 남음
                              </span>
                            </div>
                            <button
                              type="button"
                              className="pay-btn"
                              disabled={payingId === p.id}
                              onClick={() => handleConfirm(p)}
                            >
                              {payingId === p.id ? '결제 중' : '결제하기'}
                            </button>
                          </div>
                        ) : (
                          <button
                            type="button"
                            className="order-btn"
                            disabled={soldOut || orderingId === p.id}
                            onClick={() => handleOrder(p)}
                          >
                            {soldOut ? '품절' : orderingId === p.id ? '주문 중' : '주문하기'}
                          </button>
                        )}

                        {card?.msg && (
                          <p className={`card-note ${card.state === 'paid' ? 'success' : 'error'}`}>
                            {card.msg}
                          </p>
                        )}
                      </article>
                    )
                  })}
                </section>
              </>
            )}
          </>
        )}

        {view === 'payment' && payTarget && (
          <section className="sheet">
            <h2 className="sheet-title">결제</h2>
            <p className="sheet-sub">
              {payTarget.title} · ₩{payTarget.price.toLocaleString('ko-KR')}
            </p>
            <div id="toss-payment-method" />
            <div id="toss-agreement" />
            <button
              type="button"
              className="sheet-submit"
              disabled={!tossWidgets}
              onClick={requestTossPayment}
            >
              {tossWidgets ? '결제하기' : '결제창 불러오는 중'}
            </button>
            {formError && <p className="sheet-error">{formError}</p>}
            <button type="button" className="text-btn sheet-switch" onClick={() => goto('home')}>
              돌아가기 (예약은 유지됩니다)
            </button>
          </section>
        )}

        {(view === 'login' || view === 'signup') && (
          <section className="sheet">
            <h2 className="sheet-title">{view === 'signup' ? '회원가입' : '로그인'}</h2>
            <p className="sheet-sub">
              {view === 'signup' ? '수수깡에서 작품을 만나보세요.' : '다시 만나 반가워요.'}
            </p>
            <form onSubmit={handleAuthSubmit}>
              <label className="field">
                이메일
                <input
                  type="email"
                  required
                  autoComplete="email"
                  value={form.email ?? ''}
                  onChange={setField('email')}
                />
              </label>
              <label className="field">
                비밀번호
                <input
                  type="password"
                  required
                  autoComplete={view === 'signup' ? 'new-password' : 'current-password'}
                  value={form.password ?? ''}
                  onChange={setField('password')}
                />
              </label>
              <button type="submit" className="sheet-submit" disabled={formBusy}>
                {formBusy ? '처리 중' : view === 'signup' ? '가입하기' : '로그인'}
              </button>
            </form>
            {formNotice && <p className="sheet-notice">{formNotice}</p>}
            {formError && <p className="sheet-error">{formError}</p>}
            <button
              type="button"
              className="text-btn sheet-switch"
              onClick={() => goto(view === 'signup' ? 'login' : 'signup')}
            >
              {view === 'signup' ? '이미 계정이 있다면 로그인' : '계정이 없다면 회원가입'}
            </button>
          </section>
        )}

        {view === 'notifications' && (
          <section className="sheet wide">
            <h2 className="sheet-title">알림</h2>
            <p className="sheet-sub">주문이 접수되면 알림이 도착합니다.</p>

            {notificationsState === 'loading' && <p className="status-box">불러오는 중입니다.</p>}
            {notificationsState === 'error' && (
              <div className="status-box">
                <p>알림을 불러오지 못했습니다.</p>
                <button type="button" className="ghost-btn" onClick={loadNotifications}>
                  다시 시도
                </button>
              </div>
            )}
            {notificationsState === 'ready' && notifications.length === 0 && (
              <p className="status-box">도착한 알림이 없습니다.</p>
            )}
            {notificationsState === 'ready' &&
              notifications.map(n => (
                <div className="note-item" key={n.id}>
                  <p className="note-msg">{n.message}</p>
                  <span className="note-time">
                    {new Date(n.createdAt).toLocaleString('ko-KR', {
                      month: 'numeric',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </span>
                </div>
              ))}
          </section>
        )}

        {view === 'settlements' && (
          <section className="sheet wide">
            <h2 className="sheet-title">내 정산 내역</h2>
            <p className="sheet-sub">내가 등록한 작품의 결제 확정 건이 기록됩니다.</p>

            {settlementsState === 'loading' && <p className="status-box">불러오는 중입니다.</p>}
            {settlementsState === 'error' && (
              <div className="status-box">
                <p>정산 내역을 불러오지 못했습니다.</p>
                <button type="button" className="ghost-btn" onClick={loadSettlements}>
                  다시 시도
                </button>
              </div>
            )}
            {settlementsState === 'ready' && settlements.length === 0 && (
              <p className="status-box">아직 정산된 주문이 없습니다.</p>
            )}
            {settlementsState === 'ready' && settlements.length > 0 && (
              <table className="stable">
                <thead>
                  <tr>
                    <th>주문번호</th>
                    <th>작품</th>
                    <th>정산 금액</th>
                    <th>정산 시각</th>
                  </tr>
                </thead>
                <tbody>
                  {settlements.map(s => (
                    <tr key={s.orderId}>
                      <td>#{s.orderId}</td>
                      <td>{products.find(p => p.id === s.productId)?.title ?? `상품 ${s.productId}`}</td>
                      <td className="amount">₩{s.amount.toLocaleString('ko-KR')}</td>
                      <td>
                        {new Date(s.settledAt).toLocaleString('ko-KR', {
                          month: 'numeric',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        )}

        {view === 'register' && (
          <section className="sheet">
            <h2 className="sheet-title">작품 등록</h2>
            <p className="sheet-sub">한정 수량으로 판매할 작품을 올려 주세요.</p>
            <form onSubmit={handleRegisterSubmit}>
              <label className="field">
                작품명
                <input required maxLength={60} value={form.title ?? ''} onChange={setField('title')} />
              </label>
              <label className="field">
                가격 (원)
                <input
                  type="number"
                  required
                  min="0"
                  step="100"
                  value={form.price ?? ''}
                  onChange={setField('price')}
                />
              </label>
              <label className="field">
                한정 수량 (개)
                <input
                  type="number"
                  required
                  min="1"
                  value={form.quantity ?? ''}
                  onChange={setField('quantity')}
                />
              </label>
              <button type="submit" className="sheet-submit" disabled={formBusy}>
                {formBusy ? '등록 중' : '등록하기'}
              </button>
            </form>
            {formError && <p className="sheet-error">{formError}</p>}
          </section>
        )}
      </main>

      <footer className="foot">
        <div className="wrap">
          <p>수수깡 — 수공예 판매 마켓</p>
        </div>
      </footer>
    </>
  )
}

export default App
