/*
 * Zed Attack Proxy (ZAP) and its related class files.
 * 
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0 
 *   
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */
package org.zaproxy.zap.extension.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.ImageIcon;

import org.apache.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.control.Control;
import org.parosproxy.paros.control.Control.Mode;
import org.parosproxy.paros.db.Database;
import org.parosproxy.paros.db.DatabaseException;
import org.parosproxy.paros.db.DatabaseUnsupportedException;
import org.parosproxy.paros.db.RecordSessionUrl;
import org.parosproxy.paros.extension.ExtensionAdaptor;
import org.parosproxy.paros.extension.ExtensionHook;
import org.parosproxy.paros.extension.ExtensionHookMenu;
import org.parosproxy.paros.extension.ExtensionHookView;
import org.parosproxy.paros.extension.ExtensionLoader;
import org.parosproxy.paros.extension.SessionChangedListener;
import org.parosproxy.paros.extension.ViewDelegate;
import org.parosproxy.paros.extension.manualrequest.ExtensionManualRequestEditor;
import org.parosproxy.paros.extension.manualrequest.ManualRequestEditorDialog;
import org.parosproxy.paros.extension.manualrequest.http.impl.ManualHttpRequestEditorDialog;
import org.parosproxy.paros.model.HistoryReference;
import org.parosproxy.paros.model.Model;
import org.parosproxy.paros.model.Session;
import org.parosproxy.paros.model.SiteNode;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpRequestHeader;
import org.parosproxy.paros.network.HttpSender;
import org.parosproxy.paros.view.AbstractParamPanel;
import org.parosproxy.paros.view.View;
import org.zaproxy.zap.PersistentConnectionListener;
import org.zaproxy.zap.ZapGetMethod;
import org.zaproxy.zap.extension.brk.BreakpointMessageHandler2;
import org.zaproxy.zap.extension.brk.ExtensionBreak;
import org.zaproxy.zap.extension.help.ExtensionHelp;
import org.zaproxy.zap.extension.httppanel.Message;
import org.zaproxy.zap.extension.httppanel.component.HttpPanelComponentInterface;
import org.zaproxy.zap.extension.httppanel.view.HttpPanelDefaultViewSelector;
import org.zaproxy.zap.extension.httppanel.view.HttpPanelView;
import org.zaproxy.zap.extension.httppanel.view.hex.HttpPanelHexView;
import org.zaproxy.zap.extension.script.ExtensionScript;
import org.zaproxy.zap.extension.script.ScriptType;
import org.zaproxy.zap.extension.websocket.brk.PopupMenuAddBreakWebSocket;
import org.zaproxy.zap.extension.websocket.brk.WebSocketBreakpointMessageHandler;
import org.zaproxy.zap.extension.websocket.brk.WebSocketBreakpointsUiManagerInterface;
import org.zaproxy.zap.extension.websocket.brk.WebSocketProxyListenerBreak;
import org.zaproxy.zap.extension.websocket.db.TableWebSocket;
import org.zaproxy.zap.extension.websocket.db.WebSocketStorage;
import org.zaproxy.zap.extension.websocket.manualsend.ManualWebSocketSendEditorDialog;
import org.zaproxy.zap.extension.websocket.manualsend.WebSocketPanelSender;
import org.zaproxy.zap.extension.websocket.ui.ExcludeFromWebSocketsMenuItem;
import org.zaproxy.zap.extension.websocket.ui.OptionsParamWebSocket;
import org.zaproxy.zap.extension.websocket.ui.OptionsWebSocketPanel;
import org.zaproxy.zap.extension.websocket.ui.PopupExcludeWebSocketContextMenu;
import org.zaproxy.zap.extension.websocket.ui.PopupIncludeWebSocketContextMenu;
import org.zaproxy.zap.extension.websocket.ui.ResendWebSocketMessageMenuItem;
import org.zaproxy.zap.extension.websocket.ui.SessionExcludeFromWebSocket;
import org.zaproxy.zap.extension.websocket.ui.WebSocketPanel;
import org.zaproxy.zap.extension.websocket.ui.httppanel.component.WebSocketComponent;
import org.zaproxy.zap.extension.websocket.ui.httppanel.models.ByteWebSocketPanelViewModel;
import org.zaproxy.zap.extension.websocket.ui.httppanel.models.StringWebSocketPanelViewModel;
import org.zaproxy.zap.extension.websocket.ui.httppanel.views.WebSocketSyntaxHighlightTextView;
import org.zaproxy.zap.extension.websocket.ui.httppanel.views.large.WebSocketLargePayloadUtil;
import org.zaproxy.zap.extension.websocket.ui.httppanel.views.large.WebSocketLargePayloadView;
import org.zaproxy.zap.extension.websocket.ui.httppanel.views.large.WebSocketLargetPayloadViewModel;
import org.zaproxy.zap.network.HttpSenderListener;
import org.zaproxy.zap.view.HttpPanelManager;
import org.zaproxy.zap.view.HttpPanelManager.HttpPanelComponentFactory;
import org.zaproxy.zap.view.HttpPanelManager.HttpPanelDefaultViewSelectorFactory;
import org.zaproxy.zap.view.HttpPanelManager.HttpPanelViewFactory;
import org.zaproxy.zap.view.SiteMapListener;
import org.zaproxy.zap.view.SiteMapTreeCellRenderer;
 
/**
 * The WebSockets-extension takes over after the HTTP based WebSockets handshake
 * is finished.
 * 
 * @author Robert Koch
 */
public class ExtensionWebSocket extends ExtensionAdaptor implements
		PersistentConnectionListener, SessionChangedListener, SiteMapListener {
    
	private static final Logger logger = Logger.getLogger(ExtensionWebSocket.class);
	
	public static final int HANDSHAKE_LISTENER = 10;
	
	/**
	 * Name of this extension.
	 */
	public static final String NAME = "ExtensionWebSocket";

	/**
	 * Used to identify the type of Websocket sender scripts
	 */
	public static final String SCRIPT_TYPE_WEBSOCKET_SENDER = "websocketsender";

	/**
	 * Icon to be shown in script tab for sender scripts
	 */
	private static final ImageIcon WEBSOCKET_SENDER_SCRIPT_ICON = new ImageIcon(
			ExtensionWebSocket.class.getResource("/org/zaproxy/zap/extension/websocket/resources/script-plug.png"));

	/**
	 * Used to shorten the time, a listener is started on a WebSocket channel.
	 */
	private ExecutorService listenerThreadPool;

	/**
	 * List of observers where each element is informed on all channel's
	 * messages.
	 */
	private List<WebSocketObserver> allChannelObservers;
	
	/**
	 * List of sender listeners where each element is informed on all channel's
	 * messages.
	 */
	private List<WebSocketSenderListener> allChannelSenderListeners;

	/**
	 * Contains all proxies with their corresponding handshake message.
	 */
	private Map<Integer, WebSocketProxy> wsProxies;

	/**
	 * Database table.
	 */
	private TableWebSocket table;

	/**
	 * Interface to database.
	 */
	private WebSocketStorage storage;

	/**
	 * Different options in config.xml can change this extension's behavior.
	 */
	private OptionsParamWebSocket config;

	/**
	 * Current mode of ZAP. Determines if "unsafe" actions are allowed.
	 */
	private Mode mode;

	/**
	 * Messages for some {@link WebSocketProxy} on this list are just
	 * forwarded, but not stored nor shown in UI.
	 */
	private List<Pattern> preparedIgnoredChannels;

	/**
	 * Contains raw regex values, as they appear in the sessions dialogue.
	 */
	private List<String> ignoredChannelList;

	/**
	 * Flag that controls if the WebSockets tab should be focused when a handshake message is received.
	 * <p>
	 * Current behaviour is to focus just once.
	 * 
	 * @see #initView(ViewDelegate)
	 * @see #onHandshakeResponse(HttpMessage, Socket, ZapGetMethod)
	 */
	private boolean focusWebSocketsTabOnHandshake;
	
	private WebSocketAPI api = new WebSocketAPI(this);

	/**
	 * A {@link HttpSenderListener} implementation for removing Websocket extensions, such as compression.
	 */
	private HttpSenderListenerImpl httpSenderListener;

	/**
	 * A {@link WebSocketSenderScriptListener} that runs user-provided Websocket sender scripts.
	 */
	private WebSocketSenderScriptListener webSocketSenderScriptListener;

	/**
	 * Script type used to register Websocket sender scripts.
	 */
	private ScriptType websocketSenderSciptType;

	public ExtensionWebSocket() {
		super(NAME);
		
		// should be initialized after ExtensionBreak (24) and
		// ExtensionFilter (8) and ManualRequestEditor (36)
		setOrder(150);
	}
	
	@Override
	public void init() {
		super.init();
		
		allChannelObservers = new ArrayList<>();
		allChannelSenderListeners = new ArrayList<>();
		wsProxies = new HashMap<>();
		httpSenderListener = new HttpSenderListenerImpl();
		config = new OptionsParamWebSocket();
		
		preparedIgnoredChannels = new ArrayList<>();
		ignoredChannelList = new ArrayList<>();

		mode = Control.getSingleton().getMode();
	}
	
	@Override
	public void initView(ViewDelegate view) {
		super.initView(view);

		focusWebSocketsTabOnHandshake = true;
	}

    @Override
    public void databaseOpen(Database db) throws DatabaseException, DatabaseUnsupportedException {
		table = new TableWebSocket();
		db.addDatabaseListener(table);
		try {
			table.databaseOpen(db.getDatabaseServer());

			if (storage == null) {
				storage = new WebSocketStorage(table);	
				addAllChannelObserver(storage);
			} else {
				storage.setTable(table);
			}
			if (View.isInitialised()) {
				getWebSocketPanel().setTable(table);
				// Will have been paused when the session was about to change
				getWebSocketPanel().resume();
			}
			
			WebSocketProxy.setChannelIdGenerator(table.getMaxChannelId());

		} catch (SQLException e) {
			logger.warn(e.getMessage(), e);
		}
    }

	/**
	 * This method interweaves the WebSocket extension with the rest of ZAP.
	 * <p>
	 * It does the following things:
	 * <ul>
	 * <li>listens to new WebSocket connections</li>
	 * <li>installs itself as session listener in order to react on session
	 * changes</li>
	 * <li>adds a WebSocket tab to the status panel (information window containing
	 * e.g.: the History tab)</li>
	 * <li>adds a WebSocket specific options panel</li>
	 * <li>sets up context menu for WebSockets panel with 'Break' & 'Exclude'</li>
	 * </ul>
	 * </p>
	 */
	@Override
	public void hook(ExtensionHook extensionHook) {
		super.hook(extensionHook);
		
		extensionHook.addApiImplementor(api);
		
		extensionHook.addPersistentConnectionListener(this);
		
		extensionHook.addSessionListener(this);
		
		extensionHook.addSiteMapListener(this);

		// setup configuration
		extensionHook.addOptionsParamSet(config);

		HttpSender.addListener(httpSenderListener);
		
		try {
			setChannelIgnoreList(Model.getSingleton().getSession().getExcludeFromProxyRegexs());
		} catch (WebSocketException e) {
			logger.warn(e.getMessage(), e);
		}
		
		if (getView() != null) {
			ExtensionLoader extLoader = Control.getSingleton().getExtensionLoader();
			ExtensionHookView hookView = extensionHook.getHookView();
			ExtensionHookMenu hookMenu = extensionHook.getHookMenu();
			
			// setup WebSocket tab
			WebSocketPanel wsPanel = getWebSocketPanel();
			wsPanel.setDisplayPanel(getView().getRequestPanel(), getView().getResponsePanel());
			
			extensionHook.addSessionListener(wsPanel.getSessionListener());
			
			addAllChannelObserver(wsPanel);
			ExtensionHelp.enableHelpKey(wsPanel, "websocket.tab");
			
			hookView.addStatusPanel(getWebSocketPanel());
			
			// setup Options Panel
			hookView.addOptionPanel(getOptionsPanel());
			
			// add 'Exclude from WebSockets' menu item to WebSocket tab context menu
			hookMenu.addPopupMenuItem(new ExcludeFromWebSocketsMenuItem(this));

			// setup Session Properties
			sessionExcludePanel =  new SessionExcludeFromWebSocket(this, config);
			getView().getSessionDialog().addParamPanel(new String[]{}, sessionExcludePanel, false);
			
			// setup Breakpoints
			ExtensionBreak extBreak = (ExtensionBreak) extLoader.getExtension(ExtensionBreak.NAME);
			if (extBreak != null) {
				// setup custom breakpoint handler
				BreakpointMessageHandler2 wsBrkMessageHandler = new WebSocketBreakpointMessageHandler(extBreak.getBreakpointManagementInterface(), config);
				wsBrkMessageHandler.setEnabledBreakpoints(extBreak.getBreakpointsEnabledList());
				
				// listen on new messages such that breakpoints can apply
				addAllChannelObserver(new WebSocketProxyListenerBreak(this, wsBrkMessageHandler));

				// pop up to add the breakpoint
				hookMenu.addPopupMenuItem(new PopupMenuAddBreakWebSocket(extBreak));
				extBreak.addBreakpointsUiManager(getBrkManager());
			}
			
			// add exclude/include scope
			hookMenu.addPopupMenuItem(new PopupIncludeWebSocketContextMenu());
			hookMenu.addPopupMenuItem(new PopupExcludeWebSocketContextMenu());
			
			// setup workpanel (window containing Request, Response & Break tab)
			initializeWebSocketsForWorkPanel();
			
			// setup manualrequest extension
			ExtensionManualRequestEditor extManReqEdit = (ExtensionManualRequestEditor) extLoader
					.getExtension(ExtensionManualRequestEditor.NAME);
			if (extManReqEdit != null) {
				WebSocketPanelSender sender = new WebSocketPanelSender();
				addAllChannelObserver(sender);
				
				sendDialog = createManualSendDialog(sender);
				extManReqEdit.addManualSendEditor(sendDialog);
				hookMenu.addToolsMenuItem(sendDialog.getMenuItem());
				
				resenderDialog = createReSendDialog(sender);

				// add 'Resend Message' menu item to WebSocket tab context menu
				hookMenu.addPopupMenuItem(new ResendWebSocketMessageMenuItem(resenderDialog));
				
				
				// setup persistent connection listener for http manual send editor
				ManualRequestEditorDialog sendEditor = extManReqEdit.getManualSendEditor(HttpMessage.class);
				if (sendEditor != null) {
					ManualHttpRequestEditorDialog httpSendEditor = (ManualHttpRequestEditorDialog) sendEditor;
					httpSendEditor.addPersistentConnectionListener(this);
				}
			}
			// setup sender script interface
			ExtensionScript extensionScript = Control.getSingleton().getExtensionLoader().getExtension(ExtensionScript.class);
			if (extensionScript != null) {
				websocketSenderSciptType = new ScriptType(
						SCRIPT_TYPE_WEBSOCKET_SENDER,
						"websocket.script.type.websocketsender",
						WEBSOCKET_SENDER_SCRIPT_ICON,
						true,
						true);
				extensionScript.registerScriptType(websocketSenderSciptType);
				webSocketSenderScriptListener = new WebSocketSenderScriptListener();
				addAllChannelSenderListener(webSocketSenderScriptListener);
			}
		}
	}
	
	@Override
	public boolean canUnload() {
		return true;
	}
	
	@Override
	public void unload() {
		super.unload();
		
		HttpSender.removeListener(httpSenderListener);

		// close all existing connections
		for (Entry<Integer, WebSocketProxy> wsEntry : wsProxies.entrySet()) {
			WebSocketProxy wsProxy = wsEntry.getValue();
			wsProxy.shutdown();
		}
		
		Control control = Control.getSingleton();
		ExtensionLoader extLoader = control.getExtensionLoader();
		
		// clear up Breakpoints
		ExtensionBreak extBreak = (ExtensionBreak) extLoader.getExtension(ExtensionBreak.NAME);
		if (extBreak != null) {
			extBreak.removeBreakpointsUiManager(getBrkManager());
		}
		
		// clear up manualrequest extension
		ExtensionManualRequestEditor extManReqEdit = (ExtensionManualRequestEditor) extLoader
				.getExtension(ExtensionManualRequestEditor.NAME);
		if (extManReqEdit != null) {
			extManReqEdit.removeManualSendEditor(WebSocketMessageDTO.class);
			
			// clear up persistent connection listener for http manual send editor
			ManualRequestEditorDialog sendEditor = extManReqEdit.getManualSendEditor(HttpMessage.class);
			if (sendEditor != null) {
				ManualHttpRequestEditorDialog httpSendEditor = (ManualHttpRequestEditorDialog) sendEditor;
				httpSendEditor.removePersistentConnectionListener(this);
			}
		}
		
		if (table != null) {
			getModel().getDb().removeDatabaseListener(table);
		}

		if (getView() != null) {
			getWebSocketPanel().unload();

			getView().getSessionDialog().removeParamPanel(sessionExcludePanel);

			clearupWebSocketsForWorkPanel();

			if (sendDialog != null) {
				sendDialog.unload();
			}

			if (resenderDialog != null) {
				resenderDialog.unload();
			}
		}

		// unregister the WebSocket Sender script type and remove the listener
		ExtensionScript extensionScript = Control.getSingleton().getExtensionLoader().getExtension(ExtensionScript.class);
		if (extensionScript != null) {
    		removeAllChannelSenderListener(webSocketSenderScriptListener);
            extensionScript.removeScripType(websocketSenderSciptType);
        }
	}

	@Override
	public String getAuthor() {
		return Constant.ZAP_TEAM;
	}
	
	@Override
	public String getDescription() {
		return Constant.messages.getString("websocket.desc");
	}

	/**
	 * Add an observer that is attached to every channel connected in future.
	 * 
	 * @param observer
	 */
	public void addAllChannelObserver(WebSocketObserver observer) {
		allChannelObservers.add(observer);
	}

	/**
	 * Removes the given {@code observer}, that was attached to every channel connected.
	 * 
	 * @param observer the observer to be removed
	 * @throws IllegalArgumentException if the given {@code observer} is {@code null}.
	 */
	public void removeAllChannelObserver(WebSocketObserver observer) {
		if (observer == null) {
			throw new IllegalArgumentException("The parameter observer must not be null.");
		}
		allChannelObservers.remove(observer);
		for (WebSocketProxy wsProxy : wsProxies.values()) {
			wsProxy.removeObserver(observer);
		}
	}
	
	/**
	 * Add an sender listener that is attached to every channel connected in future.
	 * 
	 * @param senderListener
	 */
	public void addAllChannelSenderListener(WebSocketSenderListener senderListener){
		allChannelSenderListeners.add(senderListener);
	}
	
	/**
	 * Removes the given {@code senderListener}, that was attached to every channel connected.
	 * 
	 * @param senderListener the sender listener to be removed
	 * @throws IllegalArgumentException if the given {@code senderListener} is {@code null}.
	 */
	public void removeAllChannelSenderListener(WebSocketSenderListener senderListener){
		if (senderListener == null) {
			throw new IllegalArgumentException("The parameter senderListener must not be null.");
		}
		allChannelSenderListeners.remove(senderListener);
		for (WebSocketProxy wsProxy : wsProxies.values()) {
			wsProxy.removeSenderListener(senderListener);
		}
	}

	@Override
	public int getArrangeableListenerOrder() {
		return HANDSHAKE_LISTENER;
	}

	@Override
	public boolean onHandshakeResponse(HttpMessage httpMessage, Socket inSocket, ZapGetMethod method) {
		boolean keepSocketOpen = false;
		
		if (httpMessage.isWebSocketUpgrade()) {
			logger.debug("Got WebSockets upgrade request. Handle socket connection over to WebSockets extension.");
			if (focusWebSocketsTabOnHandshake) {
				// Show the tab in case its been closed
				this.getWebSocketPanel().setTabFocus();
				// Don't constantly request focus on the tab, once is enough.
				focusWebSocketsTabOnHandshake = false;
			}
			
			if (method != null) {
				Socket outSocket = method.getUpgradedConnection();
				InputStream outReader = method.getUpgradedInputStream();
				
				keepSocketOpen = true;
				
				addWebSocketsChannel(httpMessage, inSocket, outSocket, outReader);
			} else {
				logger.error("Unable to retrieve upgraded outgoing channel.");
			}
		}
		
		return keepSocketOpen;
	}

	/**
	 * Add an open channel to this extension after
	 * HTTP handshake has been completed.
	 * 
	 * @param handshakeMessage HTTP-based handshake.
	 * @param localSocket Current connection channel from the browser to ZAP.
	 * @param remoteSocket Current connection channel from ZAP to the server.
	 * @param remoteReader Current {@link InputStream} of remote connection.
	 */
	public void addWebSocketsChannel(HttpMessage handshakeMessage, Socket localSocket, Socket remoteSocket, InputStream remoteReader) {
		try {			
			HttpRequestHeader requestHeader = handshakeMessage.getRequestHeader();
			String targetHost = requestHeader.getHostName();
			int targetPort = requestHeader.getHostPort();
			if (logger.isDebugEnabled()) {
				StringBuilder logMessage = new StringBuilder(200);
				logMessage.append("Got WebSockets channel from ");
				if (localSocket != null) {
					logMessage.append(localSocket.getInetAddress()).append(':').append(localSocket.getPort());
				} else {
					logMessage.append("ZAP");
				}
				logMessage.append(" to ");
				logMessage.append(targetHost).append(':').append(targetPort);
				
				logger.debug(logMessage.toString());
			}
			
			// parse HTTP handshake
			Map<String, String> wsExtensions = parseWebSocketExtensions(handshakeMessage);
			String wsProtocol = parseWebSocketSubProtocol(handshakeMessage);
			String wsVersion = parseWebSocketVersion(handshakeMessage);
	
			WebSocketProxy wsProxy = null;
			
			if (localSocket == remoteSocket) {
				// Its a callback
				remoteSocket = null;
			}
			
			wsProxy = WebSocketProxy.create(wsVersion, localSocket, remoteSocket, targetHost, targetPort, wsProtocol, wsExtensions);
			
			if (wsProxy.isServerMode() && this.api.getCallbackUrl(false).equals(requestHeader.getURI().toString())) {
				wsProxy.setAllowAPI(true);
				wsProxy.addObserver(api.getWebSocketObserver());
			}
			
			// set other observers and handshake reference, before starting listeners
			for (WebSocketObserver observer : allChannelObservers) {
				wsProxy.addObserver(observer);
			}
			
			// set other sender listeners and handshake reference, before starting listeners
			for (WebSocketSenderListener senderListener : allChannelSenderListeners) {
				wsProxy.addSenderListener(senderListener);
			}
			
			// wait until HistoryReference is saved to database
			if (! wsProxy.isServerMode()) {
				while (handshakeMessage.getHistoryRef() == null) {
					try {
						Thread.sleep(5);
					} catch (InterruptedException e) {
						logger.warn(e.getMessage(), e);
					}
				}
			}
			wsProxy.setHandshakeReference(handshakeMessage.getHistoryRef());
			wsProxy.setForwardOnly(isChannelIgnored(wsProxy.getDTO()));
			wsProxy.startListeners(getListenerThreadPool(), remoteReader);
			
			synchronized (wsProxies) {
				wsProxies.put(wsProxy.getChannelId(), wsProxy);
			}
		} catch (Exception e) {
			// defensive measure to catch all possible exceptions
			// cleanly close resources
			if (localSocket != null && !localSocket.isClosed()) {
				try {
					localSocket.close();
				} catch (IOException e1) {
					logger.warn(e.getMessage(), e1);
				}
			}
			
			if (remoteReader != null) {
				try {
					remoteReader.close();
				} catch (IOException e1) {
					logger.warn(e.getMessage(), e1);
				}
			}
			
			if (remoteSocket != null && !remoteSocket.isClosed()) {
				try {
					remoteSocket.close();
				} catch (IOException e1) {
					logger.warn(e.getMessage(), e1);
				}
			}
			logger.error("Adding WebSockets channel failed due to: '" + e.getClass() + "' " + e.getMessage(), e);
			return;
		}
	}

	/**
	 * Parses the negotiated WebSockets extensions. It splits them up into name
	 * and params of the extension. In future we want to look up if given
	 * extension is available as ZAP extension and then use their knowledge to
	 * process frames.
	 * <p>
	 * If multiple extensions are to be used, they can all be listed in a single
	 * {@link WebSocketProtocol#HEADER_EXTENSION} field or split between multiple
	 * instances of the {@link WebSocketProtocol#HEADER_EXTENSION} header field.
	 * 
	 * @param msg
	 * @return Map with extension name and parameter string.
	 */
	private Map<String, String> parseWebSocketExtensions(HttpMessage msg) {
		Vector<String> extensionHeaders = msg.getResponseHeader().getHeaders(
				WebSocketProtocol.HEADER_EXTENSION);

		if (extensionHeaders == null) {
			return null;
		}
		
		/*
		 * From http://tools.ietf.org/html/rfc6455#section-4.3:
		 *   extension-list = 1#extension
      	 *   extension = extension-token *( ";" extension-param )
         *   extension-token = registered-token
         *   registered-token = token
         *   extension-param = token [ "=" (token | quoted-string) ]
         *    ; When using the quoted-string syntax variant, the value
         *    ; after quoted-string unescaping MUST conform to the
         *    ; 'token' ABNF.
         *    
         * e.g.:  	Sec-WebSocket-Extensions: foo
         * 			Sec-WebSocket-Extensions: bar; baz=2
		 *      is exactly equivalent to:
		 * 			Sec-WebSocket-Extensions: foo, bar; baz=2
		 * 
		 * e.g.:	Sec-WebSocket-Extensions: deflate-stream
		 * 			Sec-WebSocket-Extensions: mux; max-channels=4; flow-control, deflate-stream
		 * 			Sec-WebSocket-Extensions: private-extension
		 */
		Map<String, String> wsExtensions = new LinkedHashMap<>();
		for (String extensionHeader : extensionHeaders) {
			for (String extension : extensionHeader.split(",")) {
				String key = extension.trim();
				String params = "";
				
				int paramsIndex = key.indexOf(";");
				if (paramsIndex != -1) {
					key = extension.substring(0, paramsIndex).trim();
					params = extension.substring(paramsIndex + 1).trim();
				}
				
				wsExtensions.put(key, params);
			}
		}
		
		/*
		 * The interpretation of any extension parameters, and what constitutes
		 * a valid response by a server to a requested set of parameters by a
		 * client, will be defined by each such extension.
		 * 
		 * Note that the order of extensions is significant!
		 */
		
		return wsExtensions;
	}

	/**
	 * Parses negotiated protocols out of the response header.
	 * <p>
	 * The {@link WebSocketProtocol#HEADER_PROTOCOL} header is only allowed to
	 * appear once in the HTTP response (but several times in the HTTP request).
	 * 
	 * A server that speaks multiple sub-protocols has to make sure it selects
	 * one based on the client's handshake and specifies it in its handshake.
	 * 
	 * @param msg
	 * @return Name of negotiated sub-protocol or null.
	 */
	private String parseWebSocketSubProtocol(HttpMessage msg) {
		String subProtocol = msg.getResponseHeader().getHeader(
				WebSocketProtocol.HEADER_PROTOCOL);
		return subProtocol;
	}

	/**
	 * The {@link WebSocketProtocol#HEADER_VERSION} header might not always
	 * contain a number. Therefore I return a string. Use the version to choose
	 * the appropriate processing class.
	 * 
	 * @param msg
	 * @return Version of the WebSockets channel, defining the protocol.
	 */
	private String parseWebSocketVersion(HttpMessage msg) {
		String version = msg.getResponseHeader().getHeader(
				WebSocketProtocol.HEADER_VERSION);
		
		if (version == null) {
			// check for requested WebSockets version
			version = msg.getRequestHeader().getHeader(WebSocketProtocol.HEADER_VERSION);
			
			if (version == null) {
				// default to version 13 if non is given, for whatever reason
				logger.debug("No " + WebSocketProtocol.HEADER_VERSION + " header was provided - try version 13");
				version = "13";
			}
		}
		
		return version;
	}

	/**
	 * Creates and returns a cached thread pool that should speed up
	 * {@link WebSocketListener}.
	 * 
	 * @return
	 */
	private ExecutorService getListenerThreadPool() {
		if (listenerThreadPool == null) {
			listenerThreadPool = Executors.newCachedThreadPool();
		}
		return listenerThreadPool;
	}

	/**
	 * Returns true if the WebSocket connection that followed the given
	 * WebSocket handshake is already alive.
	 * 
	 * @param handshakeRef
	 * @return True if connection is still alive.
	 */
	public boolean isConnected(HistoryReference handshakeRef) {
		int historyId = handshakeRef.getHistoryId();
		synchronized (wsProxies) {
			for (Entry<Integer, WebSocketProxy> entry : wsProxies.entrySet()) {
				WebSocketProxy proxy = entry.getValue();
				if (proxy.getHandshakeReference() != null && historyId == proxy.getHandshakeReference().getHistoryId()) {
					return proxy.isConnected();
				}
			}
		}
		return false;
	}

	/**
	 * Returns true if given channel id is connected.
	 * 
	 * @param channelId
	 * @return True if connection is still alive.
	 */
	public boolean isConnected(Integer channelId) {
		synchronized (wsProxies) {
			if (wsProxies.containsKey(channelId)) {
				return wsProxies.get(channelId).isConnected();
			}
		}
		return false;
	}

    /**
	 * Submitted list of strings will be interpreted as regular expression on
	 * WebSocket channel URLs.
	 * <p>
	 * While connections to those excluded URLs will be established and messages
	 * will be forwarded, nothing is stored nor can you view the communication
	 * in the UI.
	 * 
	 * @param ignoreList
     * @throws WebSocketException 
	 */
	public void setChannelIgnoreList(List<String> ignoreList) throws WebSocketException {
		preparedIgnoredChannels.clear();
		
		List<String> nonEmptyIgnoreList = new ArrayList<>();
		for (String regex : ignoreList) {
			if (regex.trim().length() > 0) {
				nonEmptyIgnoreList.add(regex);
			}
		}

		// ensure validity by compiling regular expression
		// store them for better performance
		for (String regex : nonEmptyIgnoreList) {
			if (regex.trim().length() > 0) {
				preparedIgnoredChannels.add(Pattern.compile(regex.trim(), Pattern.CASE_INSENSITIVE));
			}
		}
		
		// save list in database
		try {
			Model.getSingleton().getDb().getTableSessionUrl().setUrls(RecordSessionUrl.TYPE_EXCLUDE_FROM_WEBSOCKET, nonEmptyIgnoreList);
			ignoredChannelList = nonEmptyIgnoreList;
		} catch (DatabaseException e) {
			logger.error(e.getMessage(), e);
			
			ignoredChannelList.clear();
			preparedIgnoredChannels.clear();
			
			throw new WebSocketException("Ignore list could not be applied! Consequently no channel is ignored.");
		} finally {
			// apply to existing channels
			applyChannelIgnoreList();
		}
	}

	public List<String> getChannelIgnoreList() {
		return ignoredChannelList;
	}
	
	private void applyChannelIgnoreList() {
		synchronized (wsProxies) {
			for (Entry<Integer, WebSocketProxy> entry : wsProxies.entrySet()) {
				WebSocketProxy wsProxy = entry.getValue();
				
				if (isChannelIgnored(wsProxy.getDTO())) {
					wsProxy.setForwardOnly(true);
				} else {
					wsProxy.setForwardOnly(false);
				}
			}
		}
	}

	/**
	 * Gets the channels that match the given {@code criteria}.
	 *
	 * @param criteria the criteria
	 * @return a {@code List} containing the channels that match the given {@code criteria}.
	 * @throws DatabaseException if an error occurred while obtain the channel.
	 */
	public List<WebSocketChannelDTO> getChannels(WebSocketChannelDTO criteria) throws DatabaseException {
		if (storage != null) {
			return storage.getTable().getChannels(criteria);
		}
		return Collections.emptyList();
	}

	/**
	 * If given channel is blacklisted, then nothing should be stored. Moreover
	 * it should not appear in user interface, but messages should be forwarded.
	 * 
	 * @param wsProxy
	 * @return
	 */
	public boolean isChannelIgnored(WebSocketChannelDTO channel) {
		boolean doNotStore = false;
		
		if (config.isForwardAll()) {
			// all channels are blacklisted
			doNotStore = true;
		} else if (!preparedIgnoredChannels.isEmpty()) {
			for (Pattern p : preparedIgnoredChannels) {
				Matcher m = p.matcher(channel.getFullUri());
				if (m.matches()) {
					doNotStore = true;
					break;
				}
			}
		}
		
		return doNotStore;
	}

	/**
	 * Returns the specific websocket message
	 * @param messageId the message id
	 * @param channelId the channel id
	 * @return the websocket message
	 * @throws DatabaseException
	 */
	public WebSocketMessageDTO getWebsocketMessage(int messageId, int channelId) throws DatabaseException {
		return this.table.getMessage(messageId, channelId);
	}

	/**
	 * Returns the specified messages
	 * @param criteria the criteria to use for the messages to include
	 * @param opcodes the opcodes to return, use null for any
	 * @param inScopeChannelIds the channel ids for which messages should be included, use null for all
	 * @param offset the offset of the first message
	 * @param limit the number of messages to return
	 * @param payloadPreviewLength the maximum size of the payload to include (for a preview)
	 * @return the specified messages
	 * @throws DatabaseException
	 */
	public List<WebSocketMessageDTO> getWebsocketMessages(WebSocketMessageDTO criteria, List<Integer> opcodes, List<Integer> inScopeChannelIds, int offset, int limit, int payloadPreviewLength) throws DatabaseException {
		return this.table.getMessages(criteria, opcodes, inScopeChannelIds, offset, limit, payloadPreviewLength);
	}

	public void recordMessage(WebSocketMessageDTO message) throws DatabaseException {
		this.table.insertMessage(message);
	}

	@Override
	public void sessionChanged(final Session session) {
		// TODO
		/*
		TableWebSocket table = createTableWebSocket();
		if (View.isInitialised()) {
			getWebSocketPanel().setTable(table);
		}
		storage.setTable(table);
		
		try {
			WebSocketProxy.setChannelIdGenerator(table.getMaxChannelId());
		} catch (SQLException e) {
			logger.error("Unable to retrieve current channelId value!", e);
		}
		*/

		List<String> ignoredList = new ArrayList<>();
		try {
			List<RecordSessionUrl> recordSessionUrls = Model.getSingleton()
					.getDb().getTableSessionUrl()
					.getUrlsForType(RecordSessionUrl.TYPE_EXCLUDE_FROM_WEBSOCKET);
		
			for (RecordSessionUrl record  : recordSessionUrls) {
				ignoredList.add(record.getUrl());
			}
		} catch (DatabaseException e) {
			logger.error(e.getMessage(), e);
		} finally {
			try {
				setChannelIgnoreList(ignoredList);
			} catch (WebSocketException e) {
				logger.warn(e.getMessage(), e);
			}
		}
	}

	@Override
	public void sessionAboutToChange(Session session) {
		if (View.isInitialised()) {
			// Prevent the table from being used
			getWebSocketPanel().setTable(null);
			storage.setTable(null);
		}
		
		// close existing connections
		synchronized (wsProxies) {
			for (WebSocketProxy wsProxy : wsProxies.values()) {
				wsProxy.shutdown();
			}
			wsProxies.clear();
		}
	}

	@Override
	public void sessionScopeChanged(Session session) {
		// do nothing
	}

	@Override
	public void sessionModeChanged(Mode mode) {
		this.mode = mode;
	}

	/**
	 * Returns false when either in {@link Mode#safe} or in {@link Mode#protect}
	 * and the message's channel is not in scope. Call it if you want to do
	 * "unsafe" actions like changing payloads, catch breakpoints, send custom
	 * messages, etc.
	 * 
	 * @param message
	 * @return True if operation on message is not potentially dangerous.
	 */
	public boolean isSafe(WebSocketMessageDTO message) {
		if (mode.equals(Mode.safe)) {
			return false;
		} else if (mode.equals(Mode.protect)) {
			return message.isInScope();
		} else {
			return true;
		}
	}
	
	public WebSocketStorage getStorage() {
		return storage;
	}

	/**
	 * The {@link HttpSenderListener} responsible to apply the option {@link OptionsParamWebSocket#isRemoveExtensionsHeader()}.
	 */
	private class HttpSenderListenerImpl implements HttpSenderListener {

		private static final String SEC_WEBSOCKET_EXTENSIONS = "Sec-WebSocket-Extensions";

		@Override
		public int getListenerOrder() {
			return Integer.MAX_VALUE;
		}

		@Override
		public void onHttpRequestSend(HttpMessage msg, int initiator, HttpSender sender) {
			if (config.isRemoveExtensionsHeader()) {
				msg.getRequestHeader().setHeader(SEC_WEBSOCKET_EXTENSIONS, null);
			}
		}

		@Override
		public void onHttpResponseReceive(HttpMessage msg, int initiator, HttpSender sender) {
			// Nothing to do.
		}
	}

	/*
	 * ************************************************************************
	 * GUI specific code follows here now. It is accessed only by methods hook()
	 * and sessionChangedEventHandler() (latter calls only getWebSocketPanel()).
	 * All of this UI-related code is private and should not be accessible from
	 * outside.
	 */


	/**
	 * Displayed in the bottom area beside the History, Spider, etc. tabs.
	 */
	private WebSocketPanel panel;

	/**
	 * Will be added to the hook view. 
	 */
	private OptionsWebSocketPanel optionsPanel;

	/**
	 * Allows to set custom breakpoints, e.g.: for specific opcodes only.
	 */
	private WebSocketBreakpointsUiManagerInterface brkManager;
	
	/**
	 * WebSockets can be excluded from the current session via this GUI panel.
	 */
	private SessionExcludeFromWebSocket sessionExcludePanel;
	
	/**
	 * Send custom WebSocket messages.
	 */
	private ManualWebSocketSendEditorDialog sendDialog;

	/**
	 * Resends custom WebSocket messages.
	 */
	private ManualWebSocketSendEditorDialog resenderDialog;

	private WebSocketPanel getWebSocketPanel() {
		if (panel == null) {
			panel = new WebSocketPanel(storage.getTable(), getBrkManager());
		}
		return panel;
	}

	private WebSocketBreakpointsUiManagerInterface getBrkManager() {
		if (brkManager == null) {
			ExtensionBreak extBreak = (ExtensionBreak) Control.getSingleton().getExtensionLoader().getExtension(ExtensionBreak.NAME);
			if (extBreak != null) {
				brkManager = new WebSocketBreakpointsUiManagerInterface(extBreak);
			}
		}
		return brkManager;
	}
	
	private AbstractParamPanel getOptionsPanel() {
		if (optionsPanel == null) {
			optionsPanel = new OptionsWebSocketPanel(config);
		}
		return optionsPanel;
	}


	private void initializeWebSocketsForWorkPanel() {
		// Add "HttpPanel" components and views.
		HttpPanelManager manager = HttpPanelManager.getInstance();
		
		// component factory for outgoing and incoming messages with Text view
		HttpPanelComponentFactory componentFactory = new WebSocketComponentFactory();
		manager.addRequestComponentFactory(componentFactory);
		manager.addResponseComponentFactory(componentFactory);

		// use same factory for request & response,
		// as Hex payloads are accessed the same way
		HttpPanelViewFactory viewFactory = new WebSocketHexViewFactory();
		manager.addRequestViewFactory(WebSocketComponent.NAME, viewFactory);
		manager.addResponseViewFactory(WebSocketComponent.NAME, viewFactory);
		
		// add the default Hex view for binary-opcode messages
		HttpPanelDefaultViewSelectorFactory viewSelectorFactory = new HexDefaultViewSelectorFactory();
		manager.addRequestDefaultViewSelectorFactory(WebSocketComponent.NAME, viewSelectorFactory);
		manager.addResponseDefaultViewSelectorFactory(WebSocketComponent.NAME, viewSelectorFactory);

		// replace the normal Text views with the ones that use syntax highlighting
		viewFactory = new SyntaxHighlightTextViewFactory();
		manager.addRequestViewFactory(WebSocketComponent.NAME, viewFactory);
		manager.addResponseViewFactory(WebSocketComponent.NAME, viewFactory);

		// support large payloads on incoming and outgoing messages
		viewFactory = new WebSocketLargePayloadViewFactory();
		manager.addRequestViewFactory(WebSocketComponent.NAME, viewFactory);
		manager.addResponseViewFactory(WebSocketComponent.NAME, viewFactory);
		
		viewSelectorFactory = new WebSocketLargePayloadDefaultViewSelectorFactory();
		manager.addRequestDefaultViewSelectorFactory(WebSocketComponent.NAME, viewSelectorFactory);
		manager.addResponseDefaultViewSelectorFactory(WebSocketComponent.NAME, viewSelectorFactory);
	}
	
	private void clearupWebSocketsForWorkPanel() {
		HttpPanelManager manager = HttpPanelManager.getInstance();
		
		// component factory for outgoing and incoming messages with Text view
		manager.removeRequestComponentFactory(WebSocketComponentFactory.NAME);
		manager.removeRequestComponents(WebSocketComponent.NAME);
		manager.removeResponseComponentFactory(WebSocketComponentFactory.NAME);
		manager.removeResponseComponents(WebSocketComponent.NAME);

		// use same factory for request & response,
		// as Hex payloads are accessed the same way
		manager.removeRequestViewFactory(WebSocketComponent.NAME, WebSocketHexViewFactory.NAME);
		manager.removeResponseViewFactory(WebSocketComponent.NAME, WebSocketHexViewFactory.NAME);
		
		// remove the default Hex view for binary-opcode messages
		manager.removeRequestDefaultViewSelectorFactory(WebSocketComponent.NAME, HexDefaultViewSelectorFactory.NAME);
		manager.removeResponseDefaultViewSelectorFactory(WebSocketComponent.NAME, HexDefaultViewSelectorFactory.NAME);

		// replace the normal Text views with the ones that use syntax highlighting
		manager.removeRequestViewFactory(WebSocketComponent.NAME, SyntaxHighlightTextViewFactory.NAME);
		manager.removeResponseViewFactory(WebSocketComponent.NAME, SyntaxHighlightTextViewFactory.NAME);

		// support large payloads on incoming and outgoing messages
		manager.removeRequestViewFactory(WebSocketComponent.NAME, WebSocketLargePayloadViewFactory.NAME);
		manager.removeResponseViewFactory(WebSocketComponent.NAME, WebSocketLargePayloadViewFactory.NAME);
		
		manager.removeRequestDefaultViewSelectorFactory(WebSocketComponent.NAME, WebSocketLargePayloadDefaultViewSelectorFactory.NAME);
		manager.removeResponseDefaultViewSelectorFactory(WebSocketComponent.NAME, WebSocketLargePayloadDefaultViewSelectorFactory.NAME);
	}

	/**
	 * The component returned by this factory contain the normal text view
	 * (without syntax highlighting).
	 */
    private static final class WebSocketComponentFactory implements HttpPanelComponentFactory {
        
        public static final String NAME = "WebSocketComponentFactory";

        @Override
        public String getName() {
            return NAME;
        }
        
        @Override
        public HttpPanelComponentInterface getNewComponent() {
            return new WebSocketComponent();
        }

        @Override
        public String getComponentName() {
            return WebSocketComponent.NAME;
        }
    }
	
	private static final class WebSocketHexViewFactory implements HttpPanelViewFactory {
        
        public static final String NAME = "WebSocketHexViewFactory";

        @Override
        public String getName() {
            return NAME;
        }
        
        @Override
        public HttpPanelView getNewView() {
			return new HttpPanelHexView(new ByteWebSocketPanelViewModel(), false);
        }

        @Override
        public Object getOptions() {
            return null;
        }
    }
	
	private static final class HexDefaultViewSelector implements HttpPanelDefaultViewSelector {

        public static final String NAME = "HexDefaultViewSelector";
        
        @Override
        public String getName() {
            return NAME;
        }
        
        @Override
        public boolean matchToDefaultView(Message aMessage) {
        	// use hex view only when previously selected
//            if (aMessage instanceof WebSocketMessageDTO) {
//                WebSocketMessageDTO msg = (WebSocketMessageDTO)aMessage;
//                
//                return (msg.opcode == WebSocketMessage.OPCODE_BINARY);
//            }
            return false;
        }

        @Override
        public String getViewName() {
            return HttpPanelHexView.NAME;
        }
        
        @Override
        public int getOrder() {
            return 20;
        }
    }

    private static final class HexDefaultViewSelectorFactory implements HttpPanelDefaultViewSelectorFactory {
        
        public static final String NAME = "HexDefaultViewSelectorFactory";
        
        private static HttpPanelDefaultViewSelector defaultViewSelector = null;
        
        private HttpPanelDefaultViewSelector getDefaultViewSelector() {
            if (defaultViewSelector == null) {
                createViewSelector();
            }
            return defaultViewSelector;
        }
        
        private synchronized void createViewSelector() {
            if (defaultViewSelector == null) {
                defaultViewSelector = new HexDefaultViewSelector();
            }
        }
        
        @Override
        public String getName() {
            return NAME;
        }
        
        @Override
        public HttpPanelDefaultViewSelector getNewDefaultViewSelector() {
            return getDefaultViewSelector();
        }
        
        @Override
        public Object getOptions() {
            return null;
        }
    }
	
    private static final class SyntaxHighlightTextViewFactory implements HttpPanelViewFactory {
        
        public static final String NAME = "SyntaxHighlightTextViewFactory";

        @Override
        public String getName() {
            return NAME;
        }
        
        @Override
        public HttpPanelView getNewView() {
            return new WebSocketSyntaxHighlightTextView(new StringWebSocketPanelViewModel());
        }
        
        @Override
        public Object getOptions() {
            return null;
        }
    }
	
	private static final class WebSocketLargePayloadViewFactory implements HttpPanelViewFactory {
		
		public static final String NAME = "WebSocketLargePayloadViewFactory";

		@Override
		public String getName() {
			return NAME;
		}

		@Override
		public HttpPanelView getNewView() {
			return new WebSocketLargePayloadView(new WebSocketLargetPayloadViewModel());
		}

		@Override
		public Object getOptions() {
			return null;
		}
	}
	
	private static final class WebSocketLargePayloadDefaultViewSelectorFactory implements HttpPanelDefaultViewSelectorFactory {
		
		public static final String NAME = "WebSocketLargePayloadDefaultViewSelectorFactory";
		private static HttpPanelDefaultViewSelector defaultViewSelector = null;
		
		private HttpPanelDefaultViewSelector getDefaultViewSelector() {
			if (defaultViewSelector == null) {
				createViewSelector();
			}
			return defaultViewSelector;
		}
		
		private synchronized void createViewSelector() {
			if (defaultViewSelector == null) {
				defaultViewSelector = new WebSocketLargePayloadDefaultViewSelector();
			}
		}
		
		@Override
		public String getName() {
			return NAME;
		}
		
		@Override
		public HttpPanelDefaultViewSelector getNewDefaultViewSelector() {
			return getDefaultViewSelector();
		}
		
		@Override
		public Object getOptions() {
			return null;
		}
	}

	private static final class WebSocketLargePayloadDefaultViewSelector implements HttpPanelDefaultViewSelector {

		public static final String NAME = "WebSocketLargePayloadDefaultViewSelector";
		
		@Override
		public String getName() {
			return NAME;
		}
		
		@Override
		public boolean matchToDefaultView(Message aMessage) {
		    return WebSocketLargePayloadUtil.isLargePayload(aMessage);
		}

		@Override
		public String getViewName() {
			return WebSocketLargePayloadView.NAME;
		}
        
        @Override
        public int getOrder() {
        	// has to come before HexDefaultViewSelector
            return 15;
        }
	}

	/**
	 * This method initializes the dialog for crafting custom messages.
	 * 
	 * @param sender
	 * 
	 * @return
	 */
	private ManualWebSocketSendEditorDialog createManualSendDialog(WebSocketPanelSender sender) {
		ManualWebSocketSendEditorDialog sendDialog = new ManualWebSocketSendEditorDialog(getWebSocketPanel().getChannelsModel(), sender, true, "websocket.manual_send");
		sendDialog.setTitle(Constant.messages.getString("websocket.manual_send.menu"));
		return sendDialog;
	}
	
	/**
	 * This method initializes the re-send WebSocket message dialog.
	 * 
	 * @param sender
	 * 
	 * @return
	 */    
	private ManualWebSocketSendEditorDialog createReSendDialog(WebSocketPanelSender sender) {
		ManualWebSocketSendEditorDialog	resendDialog = new ManualWebSocketSendEditorDialog(getWebSocketPanel().getChannelsModel(), sender, true, "websocket.manual_resend");
		resendDialog.setTitle(Constant.messages.getString("websocket.manual_send.popup"));
		return resendDialog;
	}

	@Override
	public void nodeSelected(SiteNode node) {
		// do nothing
	}

	@Override
	public void onReturnNodeRendererComponent(SiteMapTreeCellRenderer component,
			boolean leaf, SiteNode node) {
		if (leaf) {
			HistoryReference href = component.getHistoryReferenceFromNode(node);
			boolean isWebSocketNode = href != null && href.isWebSocketUpgrade();
			if (isWebSocketNode) {
				boolean isConnected = isConnected(component.getHistoryReferenceFromNode(node));
				boolean isIncluded = node.isIncludedInScope() && !node.isExcludedFromScope();
				
				setWebSocketIcon(isConnected, isIncluded, component);
			}
		}
	}

	private void setWebSocketIcon(boolean isConnected, boolean isIncluded, SiteMapTreeCellRenderer component) {
		if (isConnected) {
			if (isIncluded) {
				component.setIcon(WebSocketPanel.connectTargetIcon);
			} else {
				component.setIcon(WebSocketPanel.connectIcon);
			}
		} else {
			if (isIncluded) {
				component.setIcon(WebSocketPanel.disconnectTargetIcon);
			} else {
				component.setIcon(WebSocketPanel.disconnectIcon);
			}
		}
	}
	
	public String getCallbackUrl() {
		return this.api.getCallbackUrl(true);
	}
	
	protected WebSocketProxy getWebSocketProxy(int channelId) {
		return this.wsProxies.get(channelId);
	}
}
