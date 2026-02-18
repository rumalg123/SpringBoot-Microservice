"use client";

import { Auth0Client, createAuth0Client } from "@auth0/auth0-spa-js";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ApiRequestConfig, createApiClient } from "../lib/apiClient";

type Customer = {
  id: string;
  name: string;
  email: string;
  createdAt: string;
};

type Order = {
  id: string;
  customerId: string;
  item: string;
  quantity: number;
  createdAt: string;
};

type Paged<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

const env = {
  domain: process.env.NEXT_PUBLIC_AUTH0_DOMAIN || "",
  clientId: process.env.NEXT_PUBLIC_AUTH0_CLIENT_ID || "",
  audience: process.env.NEXT_PUBLIC_AUTH0_AUDIENCE || "",
  apiBase: process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me",
};

const emptyCustomer: Customer = {
  id: "",
  name: "",
  email: "",
  createdAt: "",
};

export default function Home() {
  const [auth0, setAuth0] = useState<Auth0Client | null>(null);
  const [isReady, setIsReady] = useState(false);
  const [isAuthed, setIsAuthed] = useState(false);
  const [profile, setProfile] = useState<Record<string, unknown> | null>(null);
  const [status, setStatus] = useState("Idle");

  const [createCustomerForm, setCreateCustomerForm] = useState({
    name: "",
    email: "",
  });

  const [createOrderForm, setCreateOrderForm] = useState({
    item: "",
    quantity: 1,
  });

  const [lookupEmail, setLookupEmail] = useState("");
  const [lookupId, setLookupId] = useState("");
  const [orderId, setOrderId] = useState("");
  const [orderCustomerId, setOrderCustomerId] = useState("");

  const [me, setMe] = useState<Customer | null>(null);
  const [byEmail, setByEmail] = useState<Customer | null>(null);
  const [byId, setById] = useState<Customer | null>(null);
  const [orders, setOrders] = useState<Paged<Order> | null>(null);
  const [ordersMine, setOrdersMine] = useState<Paged<Order> | null>(null);
  const [orderDetails, setOrderDetails] = useState<Record<string, unknown> | null>(null);

  const apiBase = useMemo(() => env.apiBase, []);

  useEffect(() => {
    const init = async () => {
      if (!env.domain || !env.clientId) {
        setStatus("Missing Auth0 config. Check NEXT_PUBLIC_AUTH0_DOMAIN and NEXT_PUBLIC_AUTH0_CLIENT_ID.");
        return;
      }

      const client = await createAuth0Client({
        domain: env.domain,
        clientId: env.clientId,
        authorizationParams: {
          audience: env.audience || undefined,
          redirect_uri: window.location.origin,
        },
      });

      if (window.location.search.includes("code=") && window.location.search.includes("state=")) {
        await client.handleRedirectCallback();
        window.history.replaceState({}, document.title, window.location.pathname);
      }

      const authed = await client.isAuthenticated();
      setAuth0(client);
      setIsAuthed(authed);
      if (authed) {
        const user = await client.getUser();
        setProfile(user ? (user as Record<string, unknown>) : null);
      } else {
        setProfile(null);
      }
      setIsReady(true);
    };

    void init();
  }, []);

  const requireAuth0 = useCallback(() => {
    if (!auth0) {
      throw new Error("Auth0 client not initialized.");
    }
    return auth0;
  }, [auth0]);

  const getToken = useCallback(async () => {
    const client = requireAuth0();
    return client.getTokenSilently();
  }, [requireAuth0]);

  const apiClient = useMemo(() => {
    if (!auth0) return null;
    return createApiClient({
      baseURL: apiBase,
      getToken: () => auth0.getTokenSilently(),
      onError: (message) => setStatus(message),
    });
  }, [auth0, apiBase]);

  const apiFetch = useCallback(
    async (path: string, init?: ApiRequestConfig, auth = true) => {
      if (!apiClient) {
        throw new Error("Auth0 client not ready.");
      }
      const config: ApiRequestConfig = {
        ...init,
        url: path,
      };
      if (!auth) {
        config.skipAuth = true;
      }
      const res = await apiClient.request(config);
      return res.data ?? null;
    },
    [apiClient]
  );

  const login = async () => {
    setStatus("Redirecting to login...");
    await requireAuth0().loginWithRedirect();
  };

  const signup = async () => {
    setStatus("Redirecting to sign up...");
    await requireAuth0().loginWithRedirect({
      authorizationParams: {
        screen_hint: "signup",
      },
    });
  };

  const logout = async () => {
    await requireAuth0().logout({
      logoutParams: { returnTo: window.location.origin },
    });
  };

  const refreshProfile = async () => {
    if (!auth0) return;
    const authed = await auth0.isAuthenticated();
    setIsAuthed(authed);
    if (authed) {
      const user = await auth0.getUser();
      setProfile(user ? (user as Record<string, unknown>) : null);
    } else {
      setProfile(null);
    }
  };

  const ensureCustomer = useCallback(async () => {
    if (!isAuthed || !apiClient) return;
    try {
      const data = await apiFetch("/customers/me");
      setMe(data);
      return;
    } catch (err) {
      const message = (err as Error).message || "";
      if (!message.startsWith("404")) {
        setStatus(message);
        return;
      }
    }

    try {
      const name =
        (profile?.name as string) ||
        (profile?.nickname as string) ||
        (profile?.email as string) ||
        "New User";
      const data = await apiFetch("/customers/register-auth0", { method: "POST", data: { name } });
      setMe(data);
      setStatus("Created customer profile from Auth0.");
    } catch (err) {
      setStatus((err as Error).message);
    }
  }, [apiClient, apiFetch, isAuthed, profile]);

  useEffect(() => {
    void ensureCustomer();
  }, [ensureCustomer]);

  const handleCreateCustomer = async () => {
    setStatus("Creating customer...");
    try {
      const data = await apiFetch("/customers", {
        method: "POST",
        data: createCustomerForm,
      });
      setStatus("Customer created.");
      setById(data);
    } catch (err) {
      setStatus((err as Error).message);
    }
  };

  const handleMe = async () => {
    setStatus("Loading profile...");
    try {
      const data = await apiFetch("/customers/me");
      setMe(data);
      setStatus("Loaded /customers/me.");
    } catch (err) {
      setStatus((err as Error).message);
    }
  };

  const handleByEmail = async () => {
    setStatus("Looking up by email...");
    try {
      const data = await apiFetch(`/customers/by-email?email=${encodeURIComponent(lookupEmail)}`);
      setByEmail(data);
      setStatus("Loaded /customers/by-email.");
    } catch (err) {
      setStatus((err as Error).message);
    }
  };

  const handleById = async () => {
    setStatus("Looking up by id...");
    try {
      const data = await apiFetch(`/customers/${lookupId}`);
      setById(data);
      setStatus("Loaded /customers/{id}.");
    } catch (err) {
      setStatus((err as Error).message);
    }
  };

  const handleOrders = async () => {
    setStatus("Loading orders...");
    try {
      const query = orderCustomerId ? `?customerId=${encodeURIComponent(orderCustomerId)}` : "";
      const data = await apiFetch(`/orders${query}`);
      setOrders(data);
      setStatus("Loaded /orders.");
    } catch (err) {
      setStatus((err as Error).message);
    }
  };

  const handleOrdersMine = async () => {
    setStatus("Loading my orders...");
    try {
      const data = await apiFetch("/orders/me");
      setOrdersMine(data);
      setStatus("Loaded /orders/me.");
    } catch (err) {
      setStatus((err as Error).message);
    }
  };

  const handleOrderDetails = async () => {
    setStatus("Loading order details...");
    try {
      const data = await apiFetch(`/orders/${orderId}/details`);
      setOrderDetails(data);
      setStatus("Loaded /orders/{id}/details.");
    } catch (err) {
      setStatus((err as Error).message);
    }
  };

  const handleCreateOrder = async () => {
    if (!me?.id) {
      setStatus("Load /customers/me first to get your customerId.");
      return;
    }
    setStatus("Creating order...");
    try {
      const data = await apiFetch("/orders", {
        method: "POST",
        data: {
          customerId: me.id,
          item: createOrderForm.item,
          quantity: Number(createOrderForm.quantity),
        },
      });
      setStatus("Order created.");
      setOrders({ content: [data], totalElements: 1, totalPages: 1, number: 0, size: 1 });
    } catch (err) {
      setStatus((err as Error).message);
    }
  };

  return (
    <main className="page">
      <section className="hero">
        <div className="pill">
          <span>Auth0</span>
          <span className="code">{env.domain || "missing-domain"}</span>
        </div>
        <h1>Microservice Control Deck</h1>
        <p>
          Login with Auth0, register customers, and hit every customer/order endpoint on the
          gateway. Keep this page open while you iterate on the backend.
        </p>
        <div className="row">
          <div className="pill">
            <span>API Base</span>
            <span className="code">{apiBase}</span>
          </div>
          <div className="status">{status}</div>
        </div>
      </section>

      <section className="grid">
        <div className="card">
          <h2>Auth</h2>
          <div className="row">
            <button onClick={login} disabled={!isReady}>
              Login
            </button>
            <button className="secondary" onClick={signup} disabled={!isReady}>
              Sign Up
            </button>
            <button className="accent" onClick={logout} disabled={!isReady}>
              Logout
            </button>
            <button className="secondary" onClick={refreshProfile} disabled={!isReady}>
              Refresh Session
            </button>
            <div className="stack">
              <div className="muted">Authenticated: {String(isAuthed)}</div>
              <div className="code">{profile ? JSON.stringify(profile, null, 2) : "No profile"}</div>
            </div>
          </div>
        </div>

        <div className="card">
          <h2>Create Customer (Auth Required)</h2>
          <div className="row">
            <input
              placeholder="Name"
              value={createCustomerForm.name}
              onChange={(e) => setCreateCustomerForm({ ...createCustomerForm, name: e.target.value })}
            />
            <input
              placeholder="Email"
              value={createCustomerForm.email}
              onChange={(e) => setCreateCustomerForm({ ...createCustomerForm, email: e.target.value })}
            />
            <button onClick={handleCreateCustomer}>POST /customers</button>
            <div className="code">{byId ? JSON.stringify(byId, null, 2) : "No response yet"}</div>
          </div>
        </div>

        <div className="card">
          <h2>Customer Lookup</h2>
          <div className="row">
            <input
              placeholder="Email"
              value={lookupEmail}
              onChange={(e) => setLookupEmail(e.target.value)}
            />
            <button onClick={handleByEmail}>GET /customers/by-email</button>
            <div className="code">{byEmail ? JSON.stringify(byEmail, null, 2) : "No response"}</div>
          </div>
          <div className="row" style={{ marginTop: 12 }}>
            <input
              placeholder="Customer ID"
              value={lookupId}
              onChange={(e) => setLookupId(e.target.value)}
            />
            <button className="secondary" onClick={handleById}>
              GET /customers/{`{id}`}
            </button>
            <div className="code">{byId ? JSON.stringify(byId, null, 2) : "No response"}</div>
          </div>
        </div>

        <div className="card">
          <h2>My Customer Record</h2>
          <div className="row">
            <button onClick={handleMe}>GET /customers/me</button>
            <div className="code">{me ? JSON.stringify(me, null, 2) : "No response"}</div>
          </div>
        </div>

        <div className="card">
          <h2>Orders</h2>
          <div className="row">
            <input
              placeholder="Optional customerId"
              value={orderCustomerId}
              onChange={(e) => setOrderCustomerId(e.target.value)}
            />
            <button onClick={handleOrders}>GET /orders</button>
            <button className="secondary" onClick={handleOrdersMine}>
              GET /orders/me
            </button>
            <div className="code">{orders ? JSON.stringify(orders, null, 2) : "No response"}</div>
            <div className="code">{ordersMine ? JSON.stringify(ordersMine, null, 2) : "No response"}</div>
          </div>
        </div>

        <div className="card">
          <h2>Order Details</h2>
          <div className="row">
            <input
              placeholder="Order ID"
              value={orderId}
              onChange={(e) => setOrderId(e.target.value)}
            />
            <button onClick={handleOrderDetails}>GET /orders/{`{id}`}/details</button>
            <div className="code">{orderDetails ? JSON.stringify(orderDetails, null, 2) : "No response"}</div>
          </div>
        </div>

        <div className="card">
          <h2>Create Order (uses /customers/me)</h2>
          <div className="row">
            <input
              placeholder="Item"
              value={createOrderForm.item}
              onChange={(e) => setCreateOrderForm({ ...createOrderForm, item: e.target.value })}
            />
            <input
              placeholder="Quantity"
              type="number"
              min={1}
              value={createOrderForm.quantity}
              onChange={(e) =>
                setCreateOrderForm({ ...createOrderForm, quantity: Number(e.target.value) })
              }
            />
            <button className="accent" onClick={handleCreateOrder}>
              POST /orders
            </button>
          </div>
        </div>
      </section>

      <div className="footer">
        <div>Frontend domain: microservicefrontend.rumalg.me</div>
        <div>Gateway domain: gateway.rumalg.me</div>
      </div>
    </main>
  );
}
