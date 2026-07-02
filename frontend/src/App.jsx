import { useEffect, useState } from 'react'

const API = 'http://localhost:8080'
const TOKEN_KEY = 'susuggang.token'
const EMAIL_KEY = 'susuggang.email'

function App() {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY))
  const [userEmail, setUserEmail] = useState(() => localStorage.getItem(EMAIL_KEY))

  const [products, setProducts] = useState([])
  const [listState, setListState] = useState('loading')

  const [authMode, setAuthMode] = useState(null)
  const [authEmail, setAuthEmail] = useState('')
  const [authPassword, setAuthPassword] = useState('')
  const [authError, setAuthError] = useState('')
  const [authNotice, setAuthNotice] = useState('')
  const [authBusy, setAuthBusy] = useState(false)

  const [orderingId, setOrderingId] = useState(null)
  const [cardNotes, setCardNotes] = useState({})

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

  function openAuth(mode) {
    setAuthMode(prev => (prev === mode ? null : mode))
    setAuthError('')
    setAuthNotice('')
  }

  function logout() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(EMAIL_KEY)
    setToken(null)
    setUserEmail(null)
  }

  async function handleAuthSubmit(e) {
    e.preventDefault()
    setAuthError('')
    setAuthNotice('')
    setAuthBusy(true)
    try {
      const res = await fetch(`${API}/auth/${authMode === 'signup' ? 'signup' : 'login'}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: authEmail, password: authPassword }),
      })
      if (!res.ok) throw new Error('http')
      if (authMode === 'signup') {
        setAuthMode('login')
        setAuthPassword('')
        setAuthNotice('가입이 완료되었습니다. 로그인해 주세요.')
      } else {
        const data = await res.json()
        localStorage.setItem(TOKEN_KEY, data.token)
        localStorage.setItem(EMAIL_KEY, authEmail)
        setToken(data.token)
        setUserEmail(authEmail)
        setAuthMode(null)
        setAuthEmail('')
        setAuthPassword('')
      }
    } catch (err) {
      if (err instanceof TypeError) {
        setAuthError('서버에 연결할 수 없습니다.')
      } else if (authMode === 'signup') {
        setAuthError('회원가입에 실패했습니다.')
      } else {
        setAuthError('이메일 또는 비밀번호를 확인해 주세요.')
      }
    } finally {
      setAuthBusy(false)
    }
  }

  function setNote(productId, type, text) {
    setCardNotes(prev => ({ ...prev, [productId]: { type, text } }))
  }

  async function handleOrder(product) {
    if (!token) {
      setNote(product.id, 'error', '로그인이 필요합니다')
      setAuthMode('login')
      return
    }
    setOrderingId(product.id)
    try {
      const res = await fetch(`${API}/orders`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ productId: product.id }),
      })
      if (res.status === 201) {
        const orderId = await res.json()
        setNote(product.id, 'success', `주문 완료 · 주문번호 ${orderId}`)
        setProducts(prev =>
          prev.map(p => (p.id === product.id ? { ...p, quantity: Math.max(0, p.quantity - 1) } : p)),
        )
      } else if (res.status === 409) {
        setNote(product.id, 'error', '품절되었습니다')
        setProducts(prev => prev.map(p => (p.id === product.id ? { ...p, quantity: 0 } : p)))
      } else if (res.status === 401 || res.status === 403) {
        setNote(product.id, 'error', '로그인이 필요합니다')
        logout()
        setAuthMode('login')
      } else {
        setNote(product.id, 'error', '주문에 실패했습니다')
      }
    } catch {
      setNote(product.id, 'error', '서버에 연결할 수 없습니다')
    } finally {
      setOrderingId(null)
    }
  }

  return (
    <>
      <header className="top">
        <div className="wrap top-inner">
          <span className="logo">수수깡</span>
          {token ? (
            <div className="account">
              <span className="account-email">{userEmail}</span>
              <button type="button" className="text-btn" onClick={logout}>
                로그아웃
              </button>
            </div>
          ) : (
            <div className="account">
              <button
                type="button"
                className={authMode === 'login' ? 'ghost-btn active' : 'ghost-btn'}
                onClick={() => openAuth('login')}
              >
                로그인
              </button>
              <button
                type="button"
                className={authMode === 'signup' ? 'ghost-btn active' : 'ghost-btn'}
                onClick={() => openAuth('signup')}
              >
                회원가입
              </button>
            </div>
          )}
        </div>
      </header>

      <main className="wrap">
        {authMode && !token && (
          <section className="auth">
            <h2 className="auth-title">{authMode === 'signup' ? '회원가입' : '로그인'}</h2>
            <form onSubmit={handleAuthSubmit}>
              <label className="field">
                이메일
                <input
                  type="email"
                  required
                  autoComplete="email"
                  value={authEmail}
                  onChange={e => setAuthEmail(e.target.value)}
                />
              </label>
              <label className="field">
                비밀번호
                <input
                  type="password"
                  required
                  autoComplete={authMode === 'signup' ? 'new-password' : 'current-password'}
                  value={authPassword}
                  onChange={e => setAuthPassword(e.target.value)}
                />
              </label>
              <button type="submit" className="auth-submit" disabled={authBusy}>
                {authBusy ? '처리 중' : authMode === 'signup' ? '가입하기' : '로그인'}
              </button>
            </form>
            {authNotice && <p className="auth-notice">{authNotice}</p>}
            {authError && <p className="auth-error">{authError}</p>}
            <button
              type="button"
              className="text-btn auth-switch"
              onClick={() => openAuth(authMode === 'signup' ? 'login' : 'signup')}
            >
              {authMode === 'signup' ? '이미 계정이 있다면 로그인' : '계정이 없다면 회원가입'}
            </button>
          </section>
        )}

        {listState === 'loading' && <p className="status-box">작품을 불러오는 중입니다.</p>}

        {listState === 'error' && (
          <div className="status-box">
            <p>상품 목록을 불러오지 못했습니다. 서버 연결을 확인해 주세요.</p>
            <button type="button" className="ghost-btn" onClick={loadProducts}>
              다시 시도
            </button>
          </div>
        )}

        {listState === 'ready' && products.length === 0 && (
          <p className="status-box">등록된 작품이 없습니다.</p>
        )}

        {listState === 'ready' && products.length > 0 && (
          <>
            <p className="count">전체 작품 {products.length}</p>
            <section className="grid">
              {products.map(p => {
                const soldOut = p.quantity <= 0 || p.status === 'SOLD_OUT'
                const note = cardNotes[p.id]
                return (
                  <article className="card" key={p.id}>
                    <div className="thumb" aria-hidden="true" />
                    <h2 className="card-title">{p.title}</h2>
                    <p className="card-price">₩{p.price.toLocaleString('ko-KR')}</p>
                    <p className="card-stock">{soldOut ? '품절' : `재고 ${p.quantity}개`}</p>
                    <button
                      type="button"
                      className="order-btn"
                      disabled={soldOut || orderingId === p.id}
                      onClick={() => handleOrder(p)}
                    >
                      {soldOut ? '품절' : orderingId === p.id ? '주문 중' : '주문하기'}
                    </button>
                    {note && <p className={`card-note ${note.type}`}>{note.text}</p>}
                  </article>
                )
              })}
            </section>
          </>
        )}
      </main>

      <footer className="foot">
        <div className="wrap">
          <p>수수깡 — 수공예 한정수량 판매</p>
        </div>
      </footer>
    </>
  )
}

export default App
