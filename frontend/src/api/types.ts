export type Role = 'CUSTOMER' | 'RESTAURANT' | 'COURIER' | 'ADMIN' | 'SUPERADMIN';
export type SizeClass = 'SMALL' | 'MEDIUM' | 'LARGE';
export type OrderStatus = 'AWAITING_PAYMENT' | 'PENDING' | 'ACCEPTED' | 'READY' | 'RESERVED' | 'LOADED' | 'IN_TRANSIT' | 'ARRIVED' | 'COLLECTED' | 'REJECTED' | 'CANCELLED';
export type CompartmentState = 'AVAILABLE' | 'RESERVED' | 'LOADING' | 'OCCUPIED' | 'LOCKED' | 'WITHDRAWING' | 'OPEN' | 'FAULT';
export type DeliveryStatus = 'OFFERED' | 'ACCEPTED' | 'PICKED_UP' | 'IN_TRANSIT' | 'ARRIVED' | 'COMPLETED' | 'CANCELLED';
export type PaymentMethod = 'CASH_ON_DELIVERY' | 'MIXX' | 'FLOOZ' | 'CARD';
export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'REFUNDED';
export type HwFault = 'NONE' | 'DOOR_AJAR' | 'LOCK_STUCK' | 'LOCK_BROKEN' | 'SENSOR_BROKEN';

export interface User { id: number; email: string; phone: string; fullName: string; roles: Role[]; status: 'ACTIVE' | 'SUSPENDED'; createdAt: string }
export interface AuthResponse { accessToken: string; refreshToken: string; user: User }
export interface Address { id: number; label: string; line: string; city: string; latitude: number; longitude: number; defaultAddress: boolean }
export interface AppConfig { paymentMethods: PaymentMethod[]; paymentsSandbox: boolean; simulatorEnabled: boolean; deliveryFee: number; commissionPercent: number; courierSharePercent: number; codeValidityMinutes: number }

export interface DishImage { id: number; key: string; url: string }
export interface Dish { id: number; restaurantId: number; categoryId: number | null; name: string; description: string; price: number; sizeClass: SizeClass; available: boolean; images: DishImage[] }
export interface Category { id: number; name: string; position: number; dishes: Dish[] }
export interface Hour { dayOfWeek: number; opensAt: string; closesAt: string }
export interface RestaurantCard { id: number; name: string; description: string; cuisine: string; logoUrl: string | null; addressLine: string; city: string; latitude: number; longitude: number; phone: string; accepting: boolean; openNow: boolean }
export interface RestaurantDetail { restaurant: RestaurantCard; hours: Hour[]; categories: Category[]; uncategorized: Dish[] }
export interface RestaurantEntity { id: number; name: string; description: string; cuisine: string; logoKey: string | null; addressLine: string; city: string; latitude: number; longitude: number; phone: string; status: 'PENDING' | 'APPROVED' | 'SUSPENDED'; accepting: boolean; createdAt: string }
export interface RestaurantProfile { restaurant: RestaurantEntity; logoUrl: string | null; openNow: boolean }

export interface OrderItem { dishId: number; name: string; unitPrice: number; quantity: number }
export interface OrderHistory { status: OrderStatus; note: string | null; at: string }
export interface OrderView { id: number; groupId: number; restaurantId: number; restaurantName: string; status: OrderStatus; subtotal: number; requiredSize: SizeClass; rejectReason: string | null; createdAt: string; updatedAt: string; items: OrderItem[]; history: OrderHistory[]; boxId: number | null; boxName: string | null; compartmentNumber: number | null; compartmentState: CompartmentState | null; deliveryId: number | null; codeActive: boolean; codeExpiresAt: string | null }
export interface PaymentView { method: PaymentMethod; status: PaymentStatus; amount: number; provider: string }
export interface GroupView { id: number; customerId: number; customerName: string; createdAt: string; deliveryAddress: string; deliveryLat: number; deliveryLng: number; subtotal: number; deliveryFee: number; total: number; payment: PaymentView | null; orders: OrderView[] }
export interface RestaurantOrder { order: OrderView; customerName: string; commission: number; net: number }
export interface CodeView { code: string; expiresAt: string; boxId: number | null; boxName: string | null; compartmentNumber: number | null }

export interface Stop { orderId: number; orderStatus: OrderStatus; restaurantName: string; restaurantAddress: string; latitude: number; longitude: number; compartmentNumber: number | null }
export interface LocationPoint { latitude: number; longitude: number; accuracyM: number | null; simulated: boolean; recordedAt: string }
export interface Tracking { deliveryId: number; status: DeliveryStatus; courierName: string; courierPhone: string; boxName: string; destLat: number; destLng: number; pickups: Stop[]; lastLocation: LocationPoint | null; path: LocationPoint[] }
export interface Mission { id: number; status: DeliveryStatus; boxId: number; boxName: string; destinationAddress: string; destLat: number; destLng: number; customerName: string; customerPhone: string; paymentMethod: PaymentMethod; paymentStatus: PaymentStatus; amountToCollect: number; earning: number; createdAt: string; completedAt: string | null; stops: Stop[] }

export interface CompartmentView { id: number; number: number; size: SizeClass; state: CompartmentState; doorOpen: boolean; locked: boolean; faultReason: string | null; orderId: number | null; mine: boolean; updatedAt: string }
export interface DeviceView { deviceUid: string; kind: 'SIMULATED' | 'ESP32'; online: boolean; batteryPct: number; temperatureC: number; displayText: string; lastSeenAt: string | null }
export interface BoxView { id: number; serial: string; name: string; status: 'ACTIVE' | 'OUT_OF_SERVICE'; courierId: number | null; courierName: string | null; courierAvailable: boolean; keypadLockedUntil: string | null; device: DeviceView | null; compartments: CompartmentView[] }
export interface HwCompartment { number: number; doorOpen: boolean; latchEngaged: boolean; mealPresent: boolean; fault: HwFault }
export interface Hardware { boxId: number; online: boolean; batteryPct: number; temperatureC: number; displayText: string; dropNextCommand: boolean; compartments: HwCompartment[] }

export interface Notification { id: number; type: string; title: string; body: string; link: string | null; readAt: string | null; createdAt: string }
export interface Complaint { id: number; userId: number; orderId: number | null; subject: string; message: string; status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED'; response: string | null; createdAt: string }
export interface DayStat { day: string; orders: number; revenue: number }
