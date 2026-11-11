// pic2filament: lógica de la página. Sin dependencias: fetch a la API REST y fragmentos Thymeleaf.
(function () {
    'use strict';

    var app = document.getElementById('app');
    var API = app.dataset.api.replace(/\/$/, '');
    var BASE = app.dataset.base.replace(/\/$/, '');
    var MAX_SUBIDA = parseInt(app.dataset.maxSubida, 10);
    var MAX_SUBIDA_TEXTO = app.dataset.maxSubidaTexto;
    var TIPOS = ['image/png', 'image/jpeg', 'image/webp'];
    var FILAMENTOS_POR_DEFECTO = ['Negro', 'Gris oscuro', 'Gris claro', 'Blanco'];

    var $ = function (sel, raiz) { return (raiz || document).querySelector(sel); };
    var $$ = function (sel, raiz) { return Array.prototype.slice.call((raiz || document).querySelectorAll(sel)); };

    var estado = {
        trabajo: null,
        biblioteca: [],
        filamentos: [],
        peticion: 0,
        temporizador: null,
        resultado: null
    };

    // ---------- Utilidades ----------

    function alerta(mensaje, tipo) {
        var div = document.createElement('div');
        div.className = 'alert alert-' + (tipo || 'danger') + ' alert-dismissible fade show shadow';
        div.setAttribute('role', 'alert');
        div.textContent = mensaje;
        var cerrar = document.createElement('button');
        cerrar.type = 'button';
        cerrar.className = 'btn-close';
        cerrar.setAttribute('data-bs-dismiss', 'alert');
        cerrar.setAttribute('aria-label', 'Cerrar');
        div.appendChild(cerrar);
        $('#alertas').appendChild(div);
        setTimeout(function () { div.classList.remove('show'); setTimeout(function () { div.remove(); }, 300); }, 7000);
    }

    function leerError(resp) {
        return resp.json().then(function (j) {
            var msg = j.mensaje || 'Error ' + resp.status;
            if (j.detalles && j.detalles.length > 1) { msg += ' (' + j.detalles.slice(1).join(' · ') + ')'; }
            return msg;
        }, function () { return 'Error ' + resp.status + ' del servidor.'; });
    }

    function num(id) {
        var v = parseFloat($('#' + id).value);
        return isNaN(v) ? null : v;
    }

    function ent(id) {
        var v = parseInt($('#' + id).value, 10);
        return isNaN(v) ? null : v;
    }

    function coma(v, dec) {
        return Number(v).toLocaleString('es-ES', { minimumFractionDigits: dec, maximumFractionDigits: dec });
    }

    function hexValido(h) { return /^#[0-9a-fA-F]{6}$/.test(h); }

    function deBiblioteca(nombre) {
        for (var i = 0; i < estado.biblioteca.length; i++) {
            if (estado.biblioteca[i].nombre === nombre) { return estado.biblioteca[i]; }
        }
        return null;
    }

    // ---------- Filamentos ----------

    function filamentosIniciales() {
        var total = ent('capasTotales') || 28;
        return FILAMENTOS_POR_DEFECTO.map(function (n, i) {
            var b = deBiblioteca(n) || { nombre: n, color: ['#111111', '#3c3c3c', '#bdbdbd', '#f7f7f5'][i], td: [0.6, 1.2, 3, 4.5][i] };
            return { nombre: b.nombre, color: b.color, td: b.td, capaInicio: i === 0 ? 1 : Math.round(1 + i * total / 4) };
        });
    }

    function pintarFilamentos() {
        var cont = $('#filamentos');
        cont.innerHTML = '';
        var manual = !$('#optimizar').checked;
        estado.filamentos.forEach(function (f, i) {
            var nodo = $('#plantilla-filamento').content.firstElementChild.cloneNode(true);
            $('.numero', nodo).textContent = i + 1;
            var sel = $('.biblioteca', nodo);
            estado.biblioteca.forEach(function (b) {
                var o = document.createElement('option');
                o.value = b.nombre;
                o.textContent = b.nombre;
                sel.appendChild(o);
            });
            var lib = deBiblioteca(f.nombre);
            sel.value = lib && lib.color.toLowerCase() === f.color.toLowerCase() ? lib.nombre : '';
            if (!sel.value) {
                var o = document.createElement('option');
                o.value = '__propio';
                o.textContent = f.nombre || 'Personalizado';
                sel.insertBefore(o, sel.children[1]);
                sel.value = '__propio';
            }
            $('.color', nodo).value = f.color;
            $('.hex', nodo).value = f.color;
            $('.td', nodo).value = f.td;
            var inicio = $('.inicio', nodo);
            inicio.value = f.capaInicio || '';
            inicio.disabled = i === 0 || !manual;
            if (i === 0) { inicio.value = 1; }
            $('.subir', nodo).disabled = i === 0;
            $('.bajar', nodo).disabled = i === estado.filamentos.length - 1;
            $('.quitar', nodo).disabled = estado.filamentos.length <= 2;

            $('.color', nodo).addEventListener('input', function (e) {
                f.color = e.target.value; f.nombre = 'Personalizado';
                $('.hex', nodo).value = f.color; programar();
            });
            $('.color', nodo).addEventListener('change', pintarFilamentos);
            $('.hex', nodo).addEventListener('input', function (e) {
                var v = e.target.value.trim();
                if (v && v[0] !== '#') { v = '#' + v; }
                if (hexValido(v)) {
                    f.color = v.toLowerCase(); f.nombre = 'Personalizado';
                    $('.color', nodo).value = f.color; programar();
                    e.target.classList.remove('is-invalid');
                } else {
                    e.target.classList.add('is-invalid');
                }
            });
            sel.addEventListener('change', function (e) {
                var b = deBiblioteca(e.target.value);
                if (b) { f.nombre = b.nombre; f.color = b.color; f.td = b.td; pintarFilamentos(); programar(); }
            });
            $('.td', nodo).addEventListener('input', function (e) {
                var v = parseFloat(e.target.value);
                if (v > 0) { f.td = v; programar(); }
            });
            inicio.addEventListener('input', function (e) {
                var v = parseInt(e.target.value, 10);
                if (v >= 1) { f.capaInicio = v; programar(); }
            });
            $('.subir', nodo).addEventListener('click', function () { mover(i, -1); });
            $('.bajar', nodo).addEventListener('click', function () { mover(i, 1); });
            $('.quitar', nodo).addEventListener('click', function () {
                estado.filamentos.splice(i, 1); pintarFilamentos(); programar();
            });
            cont.appendChild(nodo);
        });
        $('#anadir-filamento').disabled = estado.filamentos.length >= 4;
    }

    function mover(i, d) {
        var j = i + d;
        if (j < 0 || j >= estado.filamentos.length) { return; }
        var a = estado.filamentos[i];
        estado.filamentos[i] = estado.filamentos[j];
        estado.filamentos[j] = a;
        pintarFilamentos();
        programar();
    }

    // ---------- Ajustes ----------

    function ajustes() {
        return {
            anchoMm: num('anchoMm'),
            mmPorPixel: num('mmPorPixel'),
            alturaCapa: num('alturaCapa'),
            alturaPrimeraCapa: num('alturaPrimeraCapa'),
            capasBase: ent('capasBase'),
            capasTotales: ent('capasTotales'),
            filamentos: estado.filamentos.map(function (f, i) {
                return { nombre: f.nombre, color: f.color, td: f.td, capaInicio: i === 0 ? 1 : f.capaInicio };
            }),
            criterio: $('input[name=criterio]:checked').value,
            contraste: num('contraste'),
            brillo: num('brillo'),
            reducirRuido: $('#reducirRuido').checked,
            optimizar: $('#optimizar').checked,
            marcoMm: num('marcoMm') || 0
        };
    }

    function programar() {
        clearTimeout(estado.temporizador);
        if (!estado.trabajo) { return; }
        estado.temporizador = setTimeout(procesar, 350);
    }

    // ---------- Subida ----------

    function subir(fichero) {
        if (!fichero) { return; }
        if (TIPOS.indexOf(fichero.type) < 0) {
            alerta('Formato no admitido. Sube una imagen PNG, JPG o WebP.');
            return;
        }
        if (fichero.size > MAX_SUBIDA) {
            alerta('La imagen supera el tamaño máximo permitido (' + MAX_SUBIDA_TEXTO + ').');
            return;
        }
        var datos = new FormData();
        datos.append('imagen', fichero, fichero.name || 'imagen-pegada.png');
        cargando(true);
        fetch(API + '/trabajos', { method: 'POST', body: datos })
            .then(function (r) {
                if (!r.ok) { return leerError(r).then(function (m) { throw new Error(m); }); }
                return r.json();
            })
            .then(function (t) {
                estado.trabajo = t;
                var info = $('#info-imagen');
                info.textContent = t.nombre + ' · ' + t.ancho + ' × ' + t.alto + ' px';
                info.classList.remove('d-none');
                $('#detectar-colores').disabled = false;
                procesar();
            })
            .catch(function (e) { cargando(false); alerta(e.message); });
    }

    // ---------- Proceso ----------

    function procesar() {
        if (!estado.trabajo) { return; }
        var id = ++estado.peticion;
        cargando(true);
        fetch(API + '/trabajos/' + estado.trabajo.id + '/procesar', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(ajustes())
        })
            .then(function (r) {
                if (!r.ok) { return leerError(r).then(function (m) { throw new Error(m); }); }
                return r.json();
            })
            .then(function (res) {
                if (id !== estado.peticion) { return; } // llegó una respuesta más nueva
                estado.resultado = res;
                if ($('#optimizar').checked) {
                    res.capasInicio.forEach(function (c, i) { if (estado.filamentos[i]) { estado.filamentos[i].capaInicio = c; } });
                    $$('#filamentos .inicio').forEach(function (inp, i) { inp.value = res.capasInicio[i]; });
                }
                pintarResultado(res);
            })
            .catch(function (e) {
                if (id === estado.peticion) { cargando(false); alerta(e.message); }
            });
    }

    function cargando(si) {
        $('#cargando').classList.toggle('d-none', !si);
    }

    function pintarResultado(res) {
        actualizarVista();
        $('#estadisticas').textContent =
            'Pieza de ' + coma(res.anchoMm, 1) + ' × ' + coma(res.fondoMm, 1) + ' mm, altura máxima ' +
            coma(res.alturaMaximaMm, 2) + ' mm · rejilla ' + res.columnas + ' × ' + res.filas +
            ' (' + coma(res.pasoMm, 3) + ' mm) · ' + res.triangulos.toLocaleString('es-ES') + ' triángulos (' +
            coma(res.tamanoStl / 1048576, 1) + ' MB) · ' + res.tiempoMs + ' ms';
        pintarPila(res);
        fetch(BASE + '/trabajos/' + estado.trabajo.id + '/tabla-cambios')
            .then(function (r) { return r.ok ? r.text() : Promise.reject(); })
            .then(function (html) { $('#contenedor-cambios').innerHTML = html; })
            .catch(function () { });
        actualizarDescargas();
    }

    function actualizarDescargas() {
        if (!estado.trabajo || !estado.resultado) { return; }
        var base = API + '/trabajos/' + estado.trabajo.id;
        var tresmf = '/descarga.3mf?perfil=' + encodeURIComponent($('#perfil').value || '') +
            '&modo=' + $('input[name=modoCambio]:checked').value;
        [['#descargar-zip', '/descarga.zip'], ['#descargar-stl', '/descarga.stl'], ['#descargar-3mf', tresmf]]
            .forEach(function (d) {
                var a = $(d[0]);
                a.href = base + d[1];
                a.classList.remove('disabled');
                a.removeAttribute('aria-disabled');
            });
    }

    function cargarPerfiles() {
        var guardado = null;
        try { guardado = localStorage.getItem('pic2filament-perfil'); } catch (e) { }
        fetch(API + '/perfiles')
            .then(function (r) { return r.json(); })
            .then(function (lista) {
                var sel = $('#perfil');
                var hay = lista.some(function (p) { return p.id === guardado; });
                lista.forEach(function (p) {
                    var o = document.createElement('option');
                    o.value = p.id;
                    o.textContent = p.nombre;
                    if (hay ? p.id === guardado : p.porDefecto) { o.selected = true; }
                    sel.appendChild(o);
                });
                actualizarDescargas();
            })
            .catch(function () { });
        try {
            if (localStorage.getItem('pic2filament-modo-cambio') === 'manual') { $('#cambio-manual').checked = true; }
        } catch (e) { }
        ayudaModo();
    }

    function ayudaModo() {
        var manual = $('#cambio-manual').checked;
        $('#ayuda-modo').textContent = manual
            ? 'Manual: la impresora se pausa (M600) al empezar cada capa de cambio para que cambies el filamento a mano. La vista previa del laminador no mostrará los colores.'
            : 'Automático: el ACE Pro (Anycubic) o el AMS (Bambu) cambian a la ranura de cada filamento. Carga los filamentos en las ranuras 1, 2, 3 y 4 en el mismo orden que aquí.';
    }

    function actualizarVista() {
        if (!estado.trabajo || !estado.resultado) { return; }
        var modo = $('input[name=modo]:checked').value;
        var img = $('#imagen-vista');
        img.onload = function () {
            cargando(false);
            img.classList.remove('d-none');
            $('#vista-vacia').classList.add('d-none');
        };
        img.onerror = function () { cargando(false); };
        img.style.imageRendering = modo === 'alturas' ? 'pixelated' : 'auto';
        img.src = API + '/trabajos/' + estado.trabajo.id + '/vista/' + modo + '.png?v=' + estado.resultado.version;
    }

    function pintarPila(res) {
        var pila = $('#pila');
        pila.innerHTML = '';
        var base = ent('capasBase') || 0;
        var cambios = {};
        res.cambios.forEach(function (c) { if (c.filamento > 1) { cambios[c.capaInicio] = c; } });

        function fila(etiqueta, color) {
            var f = document.createElement('div');
            f.className = 'fila';
            var e = document.createElement('div');
            e.className = 'etiqueta';
            e.textContent = etiqueta;
            f.appendChild(e);
            var capas = document.createElement('div');
            capas.className = 'capas';
            res.capas.forEach(function (c) {
                var d = document.createElement('div');
                d.className = 'capa' + (c.capa <= base ? ' base' : '') + (cambios[c.capa] ? ' cambio' : '');
                d.style.backgroundColor = color(c);
                d.title = 'Capa ' + c.capa + ' · Z ' + coma(c.z, 2) + ' mm · filamento ' + c.filamento +
                    (c.capa <= base ? ' · base' : '');
                capas.appendChild(d);
            });
            f.appendChild(capas);
            return f;
        }
        pila.appendChild(fila('Filamento', function (c) { return c.colorFilamento; }));
        pila.appendChild(fila('Color visible', function (c) { return c.colorVisible; }));

        var filaMarcas = document.createElement('div');
        filaMarcas.className = 'fila';
        var hueco = document.createElement('div');
        hueco.className = 'etiqueta';
        filaMarcas.appendChild(hueco);
        var marcas = document.createElement('div');
        marcas.className = 'marcas';
        var n = res.capas.length;
        Object.keys(cambios).forEach(function (k) {
            var c = cambios[k];
            var m = document.createElement('div');
            m.className = 'marca';
            m.style.left = ((c.capaInicio - 1) / n * 100) + '%';
            m.textContent = c.capaInicio;
            m.title = 'Cambio al filamento ' + c.filamento + ' en la capa ' + c.capaInicio;
            marcas.appendChild(m);
        });
        filaMarcas.appendChild(marcas);
        pila.appendChild(filaMarcas);
    }

    // ---------- Detección de colores ----------

    function detectarColores() {
        if (!estado.trabajo) { return; }
        var boton = $('#detectar-colores');
        boton.disabled = true;
        fetch(API + '/trabajos/' + estado.trabajo.id + '/colores?cantidad=' + estado.filamentos.length, { method: 'POST' })
            .then(function (r) {
                if (!r.ok) { return leerError(r).then(function (m) { throw new Error(m); }); }
                return r.json();
            })
            .then(function (colores) {
                colores.forEach(function (c, i) {
                    var f = estado.filamentos[i];
                    f.color = c.color;
                    f.td = c.td;
                    f.nombre = c.nombre + ' (detectado)';
                });
                pintarFilamentos();
                procesar();
                alerta('Colores detectados. El TD se ha tomado del filamento más parecido de la biblioteca: ajústalo a tus filamentos.', 'info');
            })
            .catch(function (e) { alerta(e.message); })
            .finally(function () { boton.disabled = false; });
    }

    // ---------- Arranque ----------

    function restablecer() {
        $('#ajustes').reset();
        $('#contraste-valor').textContent = coma(1, 2);
        $('#brillo-valor').textContent = '0';
        estado.filamentos = filamentosIniciales();
        pintarFilamentos();
        programar();
    }

    function eventos() {
        var zona = $('#zona-subida');
        var entrada = $('#imagen');
        entrada.addEventListener('change', function () { subir(entrada.files[0]); entrada.value = ''; });
        zona.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); entrada.click(); }
        });
        ['dragenter', 'dragover'].forEach(function (t) {
            zona.addEventListener(t, function (e) { e.preventDefault(); zona.classList.add('arrastrando'); });
        });
        ['dragleave', 'drop'].forEach(function (t) {
            zona.addEventListener(t, function (e) { e.preventDefault(); zona.classList.remove('arrastrando'); });
        });
        zona.addEventListener('drop', function (e) {
            e.stopPropagation(); // la ventana también escucha «drop»: evita subirla dos veces
            if (e.dataTransfer && e.dataTransfer.files.length) { subir(e.dataTransfer.files[0]); }
        });
        // Evita que soltar una imagen fuera de la zona abra el fichero en el navegador.
        window.addEventListener('dragover', function (e) { e.preventDefault(); });
        window.addEventListener('drop', function (e) {
            e.preventDefault();
            if (e.dataTransfer && e.dataTransfer.files.length) { subir(e.dataTransfer.files[0]); }
        });
        document.addEventListener('paste', function (e) {
            var items = (e.clipboardData || {}).items || [];
            for (var i = 0; i < items.length; i++) {
                if (items[i].kind === 'file' && items[i].type.indexOf('image/') === 0) {
                    subir(items[i].getAsFile());
                    e.preventDefault();
                    return;
                }
            }
        });

        ['anchoMm', 'mmPorPixel', 'alturaCapa', 'alturaPrimeraCapa', 'capasBase', 'capasTotales', 'marcoMm']
            .forEach(function (id) { $('#' + id).addEventListener('input', programar); });
        $('#contraste').addEventListener('input', function (e) {
            $('#contraste-valor').textContent = coma(e.target.value, 2); programar();
        });
        $('#brillo').addEventListener('input', function (e) {
            $('#brillo-valor').textContent = e.target.value; programar();
        });
        $('#reducirRuido').addEventListener('change', programar);
        $$('input[name=criterio]').forEach(function (r) { r.addEventListener('change', programar); });
        $('#optimizar').addEventListener('change', function () { pintarFilamentos(); programar(); });
        $$('input[name=modo]').forEach(function (r) {
            r.addEventListener('change', function () { cargando(true); actualizarVista(); });
        });
        $('#anadir-filamento').addEventListener('click', function () {
            if (estado.filamentos.length >= 4) { return; }
            var ultimo = estado.filamentos[estado.filamentos.length - 1];
            var b = deBiblioteca('Blanco') || estado.biblioteca[0];
            var total = ent('capasTotales') || 28;
            estado.filamentos.push({ nombre: b.nombre, color: b.color, td: b.td,
                capaInicio: Math.min(total, (ultimo.capaInicio || 1) + 1) });
            pintarFilamentos();
            programar();
        });
        $('#detectar-colores').addEventListener('click', detectarColores);
        $('#restablecer').addEventListener('click', restablecer);
        $('#perfil').addEventListener('change', function (e) {
            try { localStorage.setItem('pic2filament-perfil', e.target.value); } catch (er) { }
            actualizarDescargas();
        });
        $$('input[name=modoCambio]').forEach(function (r) {
            r.addEventListener('change', function (e) {
                try { localStorage.setItem('pic2filament-modo-cambio', e.target.value); } catch (er) { }
                ayudaModo();
                actualizarDescargas();
            });
        });
        cargarPerfiles();
    }

    fetch(API + '/filamentos')
        .then(function (r) { return r.json(); })
        .catch(function () { return []; })
        .then(function (lib) {
            estado.biblioteca = lib || [];
            estado.filamentos = filamentosIniciales();
            pintarFilamentos();
            eventos();
        });
})();
