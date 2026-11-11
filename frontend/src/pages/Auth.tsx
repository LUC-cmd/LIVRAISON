import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { errorMessage, post } from '../api/client';
import type { AuthResponse } from '../api/types';
import { Field } from '../components/ui';
import { homeFor, useSession } from '../stores/session';

const loginSchema = z.object({ email: z.string().email('Email invalide.'), password: z.string().min(1, 'Mot de passe obligatoire.') });
type LoginForm = z.infer<typeof loginSchema>;

export function LoginPage() {
  const navigate = useNavigate();
  const setSession = useSession((s) => s.setSession);
  const form = useForm<LoginForm>({ resolver: zodResolver(loginSchema) });
  const login = useMutation({
    mutationFn: (values: LoginForm) => post<AuthResponse>('/auth/login', values),
    onSuccess: (auth) => { setSession(auth); navigate(homeFor(auth.user)); },
  });
  return (
    <div className="mx-auto max-w-md">
      <div className="card rise space-y-4">
        <div><h1>Connexion</h1><p className="text-sm text-ink-500">Clients, restaurants, livreurs et administrateurs.</p></div>
        <form className="space-y-3" onSubmit={form.handleSubmit((v) => login.mutate(v))} noValidate>
          <Field label="Email" error={form.formState.errors.email?.message}><input className="input" type="email" autoComplete="email" {...form.register('email')} /></Field>
          <Field label="Mot de passe" error={form.formState.errors.password?.message}><input className="input" type="password" autoComplete="current-password" {...form.register('password')} /></Field>
          {login.isError && <p className="text-sm text-red-600" role="alert">{errorMessage(login.error)}</p>}
          <button className="btn-primary w-full" disabled={login.isPending}>{login.isPending ? 'Connexion…' : 'Se connecter'}</button>
        </form>
        <p className="text-sm text-ink-500">Pas encore de compte ? <Link className="font-semibold text-brand-600" to="/inscription">Créer un compte</Link></p>
      </div>
    </div>
  );
}

const registerSchema = z.object({
  role: z.enum(['CUSTOMER', 'RESTAURANT', 'COURIER']),
  fullName: z.string().trim().min(2, 'Nom obligatoire.').max(120),
  email: z.string().email('Email invalide.'),
  phone: z.string().regex(/^\+?[0-9]{8,15}$/, '8 à 15 chiffres, par exemple +22890123456.'),
  password: z.string().min(8, '8 caractères minimum.').max(72),
});
type RegisterForm = z.infer<typeof registerSchema>;

export function RegisterPage() {
  const navigate = useNavigate();
  const setSession = useSession((s) => s.setSession);
  const form = useForm<RegisterForm>({ resolver: zodResolver(registerSchema), defaultValues: { role: 'CUSTOMER' } });
  const register = useMutation({
    mutationFn: (values: RegisterForm) => post<AuthResponse>('/auth/register', values),
    onSuccess: (auth) => { setSession(auth); navigate(homeFor(auth.user)); },
  });
  const role = form.watch('role');
  const roles = [{ v: 'CUSTOMER', l: 'Client' }, { v: 'RESTAURANT', l: 'Restaurant' }, { v: 'COURIER', l: 'Livreur' }] as const;
  return (
    <div className="mx-auto max-w-md">
      <div className="card rise space-y-4">
        <h1>Créer un compte</h1>
        <form className="space-y-3" onSubmit={form.handleSubmit((v) => register.mutate(v))} noValidate>
          <div>
            <span className="label">Je suis</span>
            <div className="grid grid-cols-3 gap-2">
              {roles.map((r) => (
                <button type="button" key={r.v} onClick={() => form.setValue('role', r.v)} className={`rounded-xl border px-2 py-2 text-sm font-semibold ${role === r.v ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-ink-100 bg-white text-ink-700'}`}>{r.l}</button>
              ))}
            </div>
          </div>
          <Field label="Nom complet" error={form.formState.errors.fullName?.message}><input className="input" autoComplete="name" {...form.register('fullName')} /></Field>
          <Field label="Email" error={form.formState.errors.email?.message}><input className="input" type="email" autoComplete="email" {...form.register('email')} /></Field>
          <Field label="Téléphone" error={form.formState.errors.phone?.message}><input className="input" type="tel" placeholder="+22890123456" autoComplete="tel" {...form.register('phone')} /></Field>
          <Field label="Mot de passe" error={form.formState.errors.password?.message}><input className="input" type="password" autoComplete="new-password" {...form.register('password')} /></Field>
          {register.isError && <p className="text-sm text-red-600" role="alert">{errorMessage(register.error)}</p>}
          <button className="btn-primary w-full" disabled={register.isPending}>{register.isPending ? 'Création…' : 'Créer mon compte'}</button>
        </form>
        <p className="text-sm text-ink-500">Déjà inscrit ? <Link className="font-semibold text-brand-600" to="/connexion">Se connecter</Link></p>
      </div>
    </div>
  );
}
