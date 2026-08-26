import express from 'express';
import fs from 'node:fs/promises';
import path from 'node:path';
import pino from 'pino';
import QRCode from 'qrcode';
import * as Baileys from '@whiskeysockets/baileys';
const {
  DisconnectReason,
  downloadMediaMessage,
  fetchLatestBaileysVersion,
  getContentType,
  jidNormalizedUser,
  useMultiFileAuthState,
} = Baileys;
const makeWASocket =
  typeof Baileys.default === 'function'
    ? Baileys.default
    : typeof Baileys.makeWASocket === 'function'
      ? Baileys.makeWASocket
      : null;

const app = express();
app.use(express.json({ limit: '100mb' }));

const PORT = Number(process.env.PORT || 21465);
const API_KEY = (process.env.BAILEYS_API_KEY || '').trim();
const DEFAULT_WEBHOOK_URL = (process.env.APP_WHATSAPP_WEBHOOK_URL || '').trim();
const AUTH_ROOT = path.resolve(process.env.BAILEYS_AUTH_DIR || '/data/auth');
const LOG_LEVEL = (process.env.BAILEYS_LOG_LEVEL || 'silent').trim();
const logger = pino({ level: LOG_LEVEL || 'silent' });

const sessions = new Map();

function nowIso() {
  return new Date().toISOString();
}

function createSessionRecord(sessionName) {
  return {
    name: sessionName,
    authDir: path.join(AUTH_ROOT, sessionName),
    sock: null,
    status: 'DISCONNECTED',
    connected: false,
    connecting: false,
    reconnecting: false,
    qrRaw: '',
    qrCodeDataUrl: '',
    webhook: DEFAULT_WEBHOOK_URL,
    lastError: '',
    lastConnectedAt: '',
    retryTimer: null,
  };
}

function getOrCreateSession(sessionName) {
  if (!sessions.has(sessionName)) {
    sessions.set(sessionName, createSessionRecord(sessionName));
  }
  return sessions.get(sessionName);
}

function serializeStatus(session) {
  return {
    session: session.name,
    status: session.status,
    connected: session.connected,
    webhook: session.webhook,
    message: session.lastError || (session.connected ? 'Conectado ao WhatsApp.' : 'Sessão não conectada.'),
    data: JSON.stringify({
      lastConnectedAt: session.lastConnectedAt || null,
      reconnecting: session.reconnecting,
      hasQrCode: Boolean(session.qrCodeDataUrl),
    }),
  };
}

function extractTextMessage(message) {
  const content = unwrapMessageContent(message);
  if (!content) {
    return '';
  }

  return (
    content.conversation ||
    content.extendedTextMessage?.text ||
    content.imageMessage?.caption ||
    content.videoMessage?.caption ||
    content.documentMessage?.caption ||
    content.buttonsResponseMessage?.selectedDisplayText ||
    content.listResponseMessage?.title ||
    content.listResponseMessage?.singleSelectReply?.title ||
    content.templateButtonReplyMessage?.selectedDisplayText ||
    ''
  ).trim();
}

function detectMediaMessageType(message) {
  const content = unwrapMessageContent(message);
  const messageType = getContentType(content || {});
  if (
    messageType === 'imageMessage' ||
    messageType === 'videoMessage' ||
    messageType === 'documentMessage' ||
    messageType === 'audioMessage' ||
    messageType === 'stickerMessage'
  ) {
    return messageType;
  }

  return '';
}

function unwrapMessageContent(message) {
  let current = message || null;

  while (current) {
    if (current.deviceSentMessage?.message) {
      current = current.deviceSentMessage.message;
      continue;
    }
    if (current.ephemeralMessage?.message) {
      current = current.ephemeralMessage.message;
      continue;
    }
    if (current.viewOnceMessage?.message) {
      current = current.viewOnceMessage.message;
      continue;
    }
    if (current.viewOnceMessageV2?.message) {
      current = current.viewOnceMessageV2.message;
      continue;
    }
    if (current.viewOnceMessageV2Extension?.message) {
      current = current.viewOnceMessageV2Extension.message;
      continue;
    }
    if (current.editedMessage?.message) {
      current = current.editedMessage.message;
      continue;
    }
    break;
  }

  return current || {};
}

function resolveRemoteJid(message) {
  return (
    String(message?.key?.remoteJid || '').trim() ||
    String(message?.message?.deviceSentMessage?.destinationJid || '').trim() ||
    String(message?.message?.editedMessage?.message?.protocolMessage?.key?.remoteJid || '').trim()
  );
}

function inferMediaMimeType(messageType, mediaMessage) {
  if (mediaMessage?.mimetype) {
    return mediaMessage.mimetype;
  }

  switch (messageType) {
    case 'imageMessage':
      return 'image/jpeg';
    case 'videoMessage':
      return 'video/mp4';
    case 'audioMessage':
      return mediaMessage?.ptt ? 'audio/ogg' : 'audio/mpeg';
    case 'stickerMessage':
      return 'image/webp';
    default:
      return 'application/octet-stream';
  }
}

function extensionFromMimeType(mimeType) {
  switch ((mimeType || '').toLowerCase()) {
    case 'image/jpeg':
      return '.jpg';
    case 'image/png':
      return '.png';
    case 'image/webp':
      return '.webp';
    case 'image/gif':
      return '.gif';
    case 'video/mp4':
      return '.mp4';
    case 'audio/ogg':
      return '.ogg';
    case 'audio/mpeg':
      return '.mp3';
    case 'audio/mp4':
      return '.m4a';
    case 'application/pdf':
      return '.pdf';
    default:
      return '';
  }
}

function buildMediaFileName(messageType, mediaMessage, mimeType) {
  const informedName = String(mediaMessage?.fileName || '').trim();
  if (informedName) {
    return informedName;
  }

  const timestamp = Date.now();
  const extension = extensionFromMimeType(mimeType);

  switch (messageType) {
    case 'imageMessage':
      return `whatsapp-image-${timestamp}${extension || '.jpg'}`;
    case 'videoMessage':
      return `whatsapp-video-${timestamp}${extension || '.mp4'}`;
    case 'audioMessage':
      return `whatsapp-audio-${timestamp}${extension || '.ogg'}`;
    case 'stickerMessage':
      return `whatsapp-sticker-${timestamp}${extension || '.webp'}`;
    default:
      return `whatsapp-file-${timestamp}${extension}`;
  }
}

async function extractMessageAttachments(message, sock) {
  const messageType = detectMediaMessageType(message?.message);
  if (!messageType) {
    return [];
  }

  const content = unwrapMessageContent(message?.message);
  const mediaMessage = content?.[messageType];
  if (!mediaMessage) {
    return [];
  }

  try {
    const buffer = await downloadMediaMessage(
      message,
      'buffer',
      {},
      {
        logger,
        reuploadRequest: sock.updateMediaMessage,
      }
    );

    if (!buffer || buffer.length === 0) {
      return [];
    }

    const contentType = inferMediaMimeType(messageType, mediaMessage);
    return [
      {
        originalFileName: buildMediaFileName(messageType, mediaMessage, contentType),
        contentType,
        sizeBytes: buffer.length,
        mediaType: messageType,
        base64: Buffer.from(buffer).toString('base64'),
      },
    ];
  } catch (error) {
    logger.warn(
      { session: sock?.user?.id || '', messageType, err: String(error) },
      'Falha ao baixar mídia recebida do WhatsApp'
    );
    return [];
  }
}

function normalizeRecipient(recipient) {
  const raw = String(recipient || '').trim();
  if (!raw) {
    throw new Error('Informe o destinatário da mensagem.');
  }

  if (raw.includes('@')) {
    if (raw.endsWith('@c.us')) {
      return raw.replace('@c.us', '@s.whatsapp.net');
    }
    return jidNormalizedUser(raw);
  }

  const digitsOnly = raw.replace(/\D+/g, '');
  if (!digitsOnly) {
    throw new Error('Informe um destinatário válido.');
  }
  return `${digitsOnly}@s.whatsapp.net`;
}

function normalizeTextMessage(message) {
  return String(message || '').trim();
}

function resolveOutgoingAttachmentKind(attachment) {
  const contentType = String(attachment?.contentType || '').trim().toLowerCase();
  const fileName = String(attachment?.originalFileName || '').trim().toLowerCase();

  if (contentType === 'image/webp' && fileName.endsWith('.webp')) {
    return 'sticker';
  }
  if (contentType.startsWith('image/')) {
    return 'image';
  }
  if (contentType.startsWith('video/')) {
    return 'video';
  }
  if (contentType.startsWith('audio/')) {
    return 'audio';
  }
  return 'document';
}

function normalizeOutgoingAttachment(attachment) {
  const originalFileName = String(attachment?.originalFileName || '').trim() || 'arquivo';
  const contentType = String(attachment?.contentType || '').trim().toLowerCase() || 'application/octet-stream';
  const base64 = String(attachment?.base64 || '').replace(/\s+/g, '');

  if (!base64) {
    throw new Error('Anexo inválido: conteúdo ausente.');
  }

  return {
    originalFileName,
    contentType,
    base64,
    kind: resolveOutgoingAttachmentKind({ originalFileName, contentType }),
  };
}

function buildOutgoingAttachmentPayload(attachment) {
  const normalizedAttachment = normalizeOutgoingAttachment(attachment);
  const buffer = Buffer.from(normalizedAttachment.base64, 'base64');

  if (!buffer.length) {
    throw new Error(`Anexo inválido: ${normalizedAttachment.originalFileName}`);
  }

  switch (normalizedAttachment.kind) {
    case 'image':
      return {
        image: buffer,
        mimetype: normalizedAttachment.contentType,
      };
    case 'video':
      return {
        video: buffer,
        mimetype: normalizedAttachment.contentType,
      };
    case 'audio':
      return {
        audio: buffer,
        mimetype: normalizedAttachment.contentType,
        ptt: true,
      };
    case 'sticker':
      return {
        sticker: buffer,
      };
    default:
      return {
        document: buffer,
        mimetype: normalizedAttachment.contentType,
        fileName: normalizedAttachment.originalFileName,
      };
  }
}

async function postWebhook(session, payload) {
  if (!session.webhook) {
    return;
  }

  try {
    const response = await fetch(session.webhook, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      logger.warn({ session: session.name, status: response.status }, 'Webhook respondeu com erro');
    }
  } catch (error) {
    logger.error({ session: session.name, err: String(error) }, 'Falha ao entregar webhook');
  }
}

async function forwardMessageEvent(session, sock, message, source, type = '') {
  const remoteJid = resolveRemoteJid(message);
  const fromMe = Boolean(message?.key?.fromMe);
  if (!remoteJid || fromMe || remoteJid.endsWith('@g.us')) {
    return;
  }

  const body = extractTextMessage(message?.message);
  const attachments = source === 'messages.update' ? [] : await extractMessageAttachments(message, sock);
  logger.info(
    {
      session: session.name,
      source,
      type,
      fromMe: false,
      remoteJid,
      bodyPreview: body.slice(0, 120),
      attachmentCount: attachments.length,
    },
    'Baileys evento de mensagem processado'
  );

  const webhookPayload = {
    event: 'onmessage',
    session: session.name,
    fromMe,
    isGroup: false,
    phone: remoteJid,
    transportId: remoteJid,
    from: remoteJid,
    chatId: remoteJid,
    body,
    attachments,
  };

  await postWebhook(session, webhookPayload);
}

async function openSocket(session) {
  if (session.connecting) {
    return;
  }
  if (typeof makeWASocket !== 'function') {
    throw new Error('Export makeWASocket não encontrado no módulo @whiskeysockets/baileys.');
  }

  session.connecting = true;
  session.lastError = '';
  session.status = session.connected ? session.status : 'CONNECTING';

  await fs.mkdir(session.authDir, { recursive: true });
  const { state, saveCreds } = await useMultiFileAuthState(session.authDir);
  const { version } = await fetchLatestBaileysVersion();

  const sock = makeWASocket({
    auth: state,
    version,
    printQRInTerminal: false,
    browser: ['Helpdesk', 'Chrome', '1.0.0'],
    logger,
  });

  session.sock = sock;

  sock.ev.on('creds.update', saveCreds);

  sock.ev.on('connection.update', async (update) => {
    if (update.qr) {
      session.qrRaw = update.qr;
      session.qrCodeDataUrl = await QRCode.toDataURL(update.qr, { margin: 1, width: 360 });
      session.status = 'QRCODE';
      session.connected = false;
    }

    if (update.connection === 'open') {
      session.connected = true;
      session.connecting = false;
      session.reconnecting = false;
      session.qrRaw = '';
      session.qrCodeDataUrl = '';
      session.lastConnectedAt = nowIso();
      session.status = 'CONNECTED';
      session.lastError = '';
    }

    if (update.connection === 'close') {
      session.connected = false;
      session.connecting = false;
      session.sock = null;

      const statusCode = update.lastDisconnect?.error?.output?.statusCode;
      const loggedOut = statusCode === DisconnectReason.loggedOut;
      session.status = loggedOut ? 'UNPAIRED' : 'DISCONNECTED';
      session.lastError = update.lastDisconnect?.error?.message || '';
      session.reconnecting = !loggedOut;

      if (!loggedOut) {
        clearTimeout(session.retryTimer);
        session.retryTimer = setTimeout(() => {
          openSocket(session).catch((error) => {
            session.connecting = false;
            session.connected = false;
            session.status = 'DISCONNECTED';
            session.lastError = String(error?.message || error);
          });
        }, 2000);
      }
    }
  });

  sock.ev.on('messages.upsert', async ({ messages, type }) => {
    for (const message of messages || []) {
      await forwardMessageEvent(session, sock, message, 'messages.upsert', type);
    }
  });

}

async function waitForSessionReadiness(session, timeoutMs = 15000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    if (session.connected || session.status === 'QRCODE') {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
}

async function resetSessionAuth(session) {
  clearTimeout(session.retryTimer);
  session.retryTimer = null;
  session.connecting = false;
  session.connected = false;
  session.reconnecting = false;
  session.sock = null;
  session.qrRaw = '';
  session.qrCodeDataUrl = '';
  session.lastError = '';
  session.status = 'DISCONNECTED';
  await fs.rm(session.authDir, { recursive: true, force: true });
  await fs.mkdir(session.authDir, { recursive: true });
}

async function startSession(sessionName, webhook, waitQrCode) {
  const session = getOrCreateSession(sessionName);
  if (webhook) {
    session.webhook = webhook;
  }

  // UNPAIRED usually means stale/invalid credentials; force new pairing.
  if (session.status === 'UNPAIRED') {
    await resetSessionAuth(session);
  }

  if (!session.sock && !session.connecting) {
    await openSocket(session);
  }

  if (waitQrCode) {
    await waitForSessionReadiness(session);
    if (session.status === 'UNPAIRED') {
      await resetSessionAuth(session);
      await openSocket(session);
      await waitForSessionReadiness(session);
    }
  }

  return session;
}

function requireApiKey(req, res, next) {
  if (!API_KEY) {
    return next();
  }

  const incoming = String(req.header('x-api-key') || '').trim();
  if (incoming !== API_KEY) {
    return res.status(401).json({
      status: 'UNAUTHORIZED',
      message: 'API key inválida.',
    });
  }
  return next();
}

app.use(requireApiKey);

app.get('/health', (_req, res) => {
  res.json({ status: 'ok' });
});

app.post('/sessions/:session/start', async (req, res) => {
  try {
    const sessionName = String(req.params.session || '').trim();
    const webhook = String(req.body?.webhook || '').trim();
    const waitQrCode = req.body?.waitQrCode !== false;
    if (!sessionName) {
      return res.status(400).json({ status: 'ERROR', message: 'Sessão inválida.' });
    }

    const session = await startSession(sessionName, webhook, waitQrCode);
    return res.json(serializeStatus(session));
  } catch (error) {
    return res.status(500).json({
      status: 'ERROR',
      message: error?.message || String(error),
    });
  }
});

app.get('/sessions/:session/status', (req, res) => {
  const sessionName = String(req.params.session || '').trim();
  if (!sessionName) {
    return res.status(400).json({ status: 'ERROR', message: 'Sessão inválida.' });
  }

  const session = getOrCreateSession(sessionName);
  return res.json(serializeStatus(session));
});

app.get('/sessions/:session/qrcode', (req, res) => {
  const sessionName = String(req.params.session || '').trim();
  if (!sessionName) {
    return res.status(400).json({ status: 'ERROR', message: 'Sessão inválida.' });
  }

  const session = getOrCreateSession(sessionName);
  if (!session.qrCodeDataUrl) {
    return res.status(404).json({
      session: session.name,
      status: session.status,
      connected: session.connected,
      message: 'QRCode ainda não disponível para esta sessão.',
      qrcode: '',
      data: '{}',
    });
  }

  return res.json({
    session: session.name,
    status: session.status,
    connected: session.connected,
    message: 'QRCode disponível para leitura.',
    qrcode: session.qrCodeDataUrl,
    data: JSON.stringify({ generatedAt: nowIso() }),
  });
});

app.post('/sessions/:session/messages', async (req, res) => {
  try {
    const sessionName = String(req.params.session || '').trim();
    if (!sessionName) {
      return res.status(400).json({ status: 'ERROR', message: 'Sessão inválida.' });
    }

    const session = getOrCreateSession(sessionName);
    if (!session.connected || !session.sock) {
      return res.status(409).json({
        status: 'DISCONNECTED',
        message: 'Sessão do WhatsApp não está conectada.',
      });
    }

    const message = normalizeTextMessage(req.body?.message);
    const attachments = Array.isArray(req.body?.attachments) ? req.body.attachments : [];
    if (!message && attachments.length === 0) {
      return res.status(400).json({ status: 'ERROR', message: 'Mensagem ou anexo obrigatório.' });
    }

    const recipient = normalizeRecipient(req.body?.phone);
    let response = null;

    if (message) {
      response = await session.sock.sendMessage(recipient, { text: message });
    }

    for (const attachment of attachments) {
      response = await session.sock.sendMessage(recipient, buildOutgoingAttachmentPayload(attachment));
    }

    return res.json({
      status: 'SUCCESS',
      message: 'Mensagem enviada com sucesso.',
      recipient,
      attachmentCount: attachments.length,
      messageId: response?.key?.id || '',
    });
  } catch (error) {
    return res.status(500).json({
      status: 'ERROR',
      message: error?.message || String(error),
    });
  }
});

app.listen(PORT, async () => {
  await fs.mkdir(AUTH_ROOT, { recursive: true });
  logger.info({ port: PORT }, 'Baileys service iniciado');
});
