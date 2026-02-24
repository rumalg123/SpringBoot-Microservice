export type PayHereFormData = {
  paymentId: string;
  merchantId: string;
  returnUrl: string;
  cancelUrl: string;
  notifyUrl: string;
  orderId: string;
  items: string;
  currency: string;
  amount: string;
  hash: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  address: string;
  city: string;
  country: string;
  checkoutUrl: string;
  custom1: string;
  custom2: string;
};

export function submitToPayHere(data: PayHereFormData) {
  const form = document.createElement("form");
  form.method = "POST";
  form.action = data.checkoutUrl;

  const fields: Record<string, string> = {
    merchant_id: data.merchantId,
    return_url: data.returnUrl,
    cancel_url: data.cancelUrl,
    notify_url: data.notifyUrl,
    order_id: data.orderId,
    items: data.items,
    currency: data.currency,
    amount: data.amount,
    hash: data.hash,
    first_name: data.firstName,
    last_name: data.lastName,
    email: data.email,
    phone: data.phone,
    address: data.address,
    city: data.city,
    country: data.country,
    custom_1: data.custom1 || "",
    custom_2: data.custom2 || "",
  };

  for (const [name, value] of Object.entries(fields)) {
    const input = document.createElement("input");
    input.type = "hidden";
    input.name = name;
    input.value = value;
    form.appendChild(input);
  }

  document.body.appendChild(form);
  form.submit();
}
