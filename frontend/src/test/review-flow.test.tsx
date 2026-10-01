import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { renderApp, signIn } from './render';

describe('AI extraction review', () => {
  it('shows confidence, flags missing fields and saves only after confirmation', async () => {
    await signIn();
    const { user, router } = renderApp('/documents/doc_airfryer_review');

    expect(await screen.findByText(/AI found 8 of 9 details/)).toBeInTheDocument();
    expect(screen.getByText(/3 fields need your attention/)).toBeInTheDocument();
    // Serial number is not on the invoice → shown as "Not found", never guessed.
    expect(screen.getAllByText('Not found').length).toBeGreaterThan(0);
    expect(screen.getAllByText('High confidence').length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: /Confirm & Save/ }));

    expect(await screen.findByRole('heading', { level: 1, name: /Philips Air Fryer HD9252\/90/ })).toBeInTheDocument();
    expect(router.state.location.pathname).toMatch(/^\/products\//);
    expect(screen.getByText('Warranty active')).toBeInTheDocument();
  });

  it('lets the user edit a value before saving', async () => {
    await signIn();
    const { user } = renderApp('/documents/doc_airfryer_review');

    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    const serial = screen.getByLabelText('Serial number');
    await user.type(serial, 'PHL-9252-0042');
    const name = screen.getByLabelText(/Product name/);
    await user.clear(name);
    await user.click(screen.getByRole('button', { name: /Confirm & Save/ }));
    expect(await screen.findByText('Product name is required')).toBeInTheDocument();

    await user.type(name, 'Philips Essential Air Fryer');
    await user.click(screen.getByRole('button', { name: /Confirm & Save/ }));
    expect(await screen.findByRole('heading', { level: 1, name: /Philips Essential Air Fryer/ })).toBeInTheDocument();
    expect(screen.getByText('PHL-9252-0042')).toBeInTheDocument();
  });
});

describe('upload validation', () => {
  it('rejects unsupported files before uploading', async () => {
    await signIn();
    renderApp('/documents');
    const user = userEvent.setup({ applyAccept: false });

    await user.click(await screen.findByRole('button', { name: 'Upload' }));
    const dialog = await screen.findByRole('dialog', { name: 'Upload bills & documents' });
    const input = within(dialog).getByTestId('file-input');
    await user.upload(input, new File(['MZ'], 'setup.exe', { type: 'application/x-msdownload' }));

    expect(await within(dialog).findByText(/Unsupported file type/)).toBeInTheDocument();
  });
});
