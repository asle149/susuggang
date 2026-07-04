import { useEffect, useState } from 'react'

const API = 'http://localhost:8080'
const TOKEN_KEY = 'susuggang.token'
const EMAIL_KEY = 'susuggang.email'
const ORDERS_KEY = 'susuggang.orders'

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

  async function loadProducts() {
    setListState('loading')
    try {
      const res = await fetch(`${API}/products`)
      if (!res.ok) throw new Error()
      setProducts(await res.json())
      setListState('ready')
    } catch {
      setListState('error')
    }
  }

  useEffect(() => {
    loadProducts()
  }, [])

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
      setSettlements(await res.json())
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
        const data = await res.json()
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
        const data = await res.json()
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

  async function handleConfirm(product) {
    const card = orderCards[product.id]
    if (!card?.orderId) return
    setPayingId(product.id)
    try {
      const res = await fetch(`${API}/orders/${card.orderId}/confirm`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      })
      if (res.ok) {
        setOrderCards(prev => ({
          ...prev,
          [product.id]: { state: 'paid', msg: `결제 완료 · 주문번호 ${card.orderId}` },
        }))
      } else if (res.status === 409) {
        setOrderCards(prev => ({
          ...prev,
          [product.id]: { state: 'expired', msg: '결제 기한이 지났습니다 · 재고는 곧 복구됩니다' },
        }))
        loadProducts()
      } else if (res.status === 401 || res.status === 403) {
        logout()
        goto('login')
      }
    } catch {
      /* 네트워크 오류 — 버튼 상태 유지, 재시도 가능 */
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
            <span className="search-icon">🔍</span>
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
